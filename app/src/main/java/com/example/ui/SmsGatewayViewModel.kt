package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ConnectRequest
import com.example.data.api.RetrofitClient
import com.example.data.db.SmsDatabase
import com.example.data.db.SmsEntity
import com.example.data.db.SmsRepository
import com.example.data.pref.AuthManager
import com.example.service.GatewayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsGatewayViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val authManager = AuthManager(context)
    private val repository: SmsRepository

    // Form inputs state
    val deviceIdInput = MutableStateFlow(authManager.getDeviceId() ?: "")
    val deviceTokenInput = MutableStateFlow(authManager.getDeviceToken() ?: "")
    val apiUrlInput = MutableStateFlow(authManager.getApiUrl())
    val isTokenVisible = MutableStateFlow(false)

    // Connection states
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError = _connectionError.asStateFlow()

    // Service State (Online/Offline, Uptime, Statistics) bound to Foreground service
    val gatewayState = GatewayService.serviceStateFlow

    // SIM Selection Preference State
    private val _selectedSimPref = MutableStateFlow(authManager.getSelectedSim())
    val selectedSimPref = _selectedSimPref.asStateFlow()

    // Active SIM Info classes retrieved dynamically
    private val _activeSimsList = MutableStateFlow<List<SimItem>>(emptyList())
    val activeSimsList = _activeSimsList.asStateFlow()

    // History Log Filter & Search State
    private val _selectedTab = MutableStateFlow("Sent") // "Sent", "Failed", "Incoming"
    val selectedTab = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    init {
        val db = SmsDatabase.getDatabase(context)
        repository = SmsRepository(db.smsDao())

        // Initial sync of stats and active SIM details
        GatewayService.updateStats(context)
        loadActiveSims()
    }

    // Dynamic class representative of SIM Details
    data class SimItem(
        val slotIndex: Int,
        val carrierName: String,
        val phoneNumber: String?,
        val subscriptionId: Int,
        val isDefault: Boolean,
        val isActive: Boolean
    )

    fun loadActiveSims() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            _activeSimsList.value = emptyList()
            return
        }

        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val rawList = subManager.activeSubscriptionInfoList
            
            val defaultSmsId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                SubscriptionManager.getDefaultSmsSubscriptionId()
            } else {
                -1
            }

            if (!rawList.isNullOrEmpty()) {
                _activeSimsList.value = rawList.map { info ->
                    SimItem(
                        slotIndex = info.simSlotIndex,
                        carrierName = info.carrierName?.toString() ?: "Unknown",
                        phoneNumber = info.number?.takeIf { it.isNotBlank() } ?: "Not reported by carrier",
                        subscriptionId = info.subscriptionId,
                        isDefault = info.subscriptionId == defaultSmsId || rawList.size == 1,
                        isActive = true
                    )
                }
            } else {
                _activeSimsList.value = emptyList()
            }
        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to retrieve subscription info: ${e.message}")
            _activeSimsList.value = emptyList()
        }
    }

    // Combined Flow: Reactive matching of tab + query search lists
    val filteredHistoryLogs: Flow<List<SmsEntity>> = combine(
        _selectedTab,
        _searchQuery,
        repository.allLogs
    ) { tab, query, allLogs ->
        allLogs.filter { log ->
            val matchesTab = when (tab) {
                "Sent" -> log.type == "Sent"
                "Failed" -> log.type == "Failed"
                "Incoming" -> log.type == "Incoming"
                else -> true
            }
            val matchesSearch = if (query.isBlank()) {
                true
            } else {
                log.phoneNumber.contains(query, ignoreCase = true) || log.message.contains(query, ignoreCase = true)
            }
            matchesTab && matchesSearch
        }
    }.flowOn(Dispatchers.Default)

    fun setTab(tab: String) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun hasSavedCredentials(): Boolean {
        return authManager.hasCredentials()
    }

    fun connectDevice(onSuccess: () -> Unit) {
        val devId = deviceIdInput.value.trim()
        val devToken = deviceTokenInput.value.trim()
        val apiUrl = apiUrlInput.value.trim()

        if (devId.isBlank() || devToken.isBlank()) {
            _connectionError.value = "Device ID and Token cannot be blank"
            return
        }

        _isConnecting.value = true
        _connectionError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("ViewModel", "Requesting connect payload at URL: $apiUrl ...")
                // Save credentials beforehand so getService(context) can use the dynamic URL
                authManager.saveCredentials(devId, devToken, apiUrl)
                
                val response = RetrofitClient.getService(context).connectDevice(ConnectRequest(devId, devToken))
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        _isConnecting.value = false
                        // Start the service
                        triggerServiceState(true)
                        onSuccess()
                    }
                } else {
                    // Revert credentials on failure
                    authManager.clearCredentials()
                    withContext(Dispatchers.Main) {
                        _isConnecting.value = false
                        _connectionError.value = "Validation failed: code ${response.code()} from server"
                    }
                }
            } catch (e: Exception) {
                // Revert credentials on failure
                authManager.clearCredentials()
                withContext(Dispatchers.Main) {
                    _isConnecting.value = false
                    _connectionError.value = e.localizedMessage ?: "Network connection failed"
                }
            }
        }
    }

    fun disconnectDevice(onCompleted: () -> Unit) {
        // Stop service first
        triggerServiceState(false)
        authManager.clearCredentials()
        deviceIdInput.value = ""
        deviceTokenInput.value = ""
        onCompleted()
    }

    fun triggerServiceState(start: Boolean) {
        val serviceIntent = Intent(context, GatewayService::class.java).apply {
            action = if (start) GatewayService.ACTION_START else GatewayService.ACTION_STOP
        }
        try {
            if (start) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to start/stop service: ${e.message}")
        }
    }

    fun updateSelectedSimPref(pref: String) {
        authManager.setSelectedSim(pref)
        _selectedSimPref.value = pref
        // Notify service of active preference change
        if (gatewayState.value.isRunning) {
            triggerServiceState(true)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearLogs()
            GatewayService.updateStats(context)
        }
    }

    fun forceRefreshState() {
        // Manually trigger a start command which forces reconnection and heartbeat
        if (gatewayState.value.isRunning) {
            triggerServiceState(true)
        }
    }

    fun parseAndApplyQrData(qrText: String): Boolean {
        val text = qrText.trim()
        if (text.isBlank()) return false
        try {
            // Case 1: JSON
            if (text.startsWith("{") && text.endsWith("}")) {
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val adapter = moshi.adapter(Map::class.java)
                val map = adapter.fromJson(text) as? Map<*, *>
                if (map != null) {
                    val id = (map["device_id"] as? String) ?: (map["id"] as? String)
                    val token = (map["device_token"] as? String) ?: (map["token"] as? String)
                    val url = (map["api_url"] as? String) ?: (map["url"] as? String) ?: (map["baseUrl"] as? String)
                    if (!id.isNullOrBlank() && !token.isNullOrBlank()) {
                        deviceIdInput.value = id
                        deviceTokenInput.value = token
                        if (!url.isNullOrBlank()) {
                            apiUrlInput.value = url
                        } else {
                            apiUrlInput.value = "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/"
                        }
                        return true
                    }
                }
            }

            // Case 2: URL containing query parameters
            if (text.startsWith("http://") || text.startsWith("https://")) {
                val uri = android.net.Uri.parse(text)
                val id = uri.getQueryParameter("device_id") ?: uri.getQueryParameter("id")
                val token = uri.getQueryParameter("device_token") ?: uri.getQueryParameter("token")
                val urlParam = uri.getQueryParameter("api_url") ?: uri.getQueryParameter("url")
                if (!id.isNullOrBlank() && !token.isNullOrBlank()) {
                    deviceIdInput.value = id
                    deviceTokenInput.value = token
                    val cleanUrl = urlParam ?: text.substringBefore("?")
                    apiUrlInput.value = cleanUrl
                    return true
                }
            }

            // Case 3: separated by comma, vertical bar, or semicolon
            val separators = listOf("|", ",", ";")
            for (sep in separators) {
                if (text.contains(sep)) {
                    val parts = text.split(sep).map { it.trim() }
                    if (parts.size >= 2) {
                        val id = parts[0]
                        val token = parts[1]
                        val url = parts.getOrNull(2)
                        if (id.isNotBlank() && token.isNotBlank()) {
                            deviceIdInput.value = id
                            deviceTokenInput.value = token
                            if (!url.isNullOrBlank()) {
                                apiUrlInput.value = url
                            } else {
                                apiUrlInput.value = "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/"
                            }
                            return true
                        }
                    }
                }
            }

            // Case 4: No separator, check if it's dual-line or space-separated
            val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size >= 2) {
                deviceIdInput.value = lines[0]
                deviceTokenInput.value = lines[1]
                if (lines.size >= 3) {
                    apiUrlInput.value = lines[2]
                } else {
                    apiUrlInput.value = "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/"
                }
                return true
            }

        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to parse QR text: ${e.message}")
        }
        return false
    }
}
