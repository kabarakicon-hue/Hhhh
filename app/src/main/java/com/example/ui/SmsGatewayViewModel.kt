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
import com.example.util.SecurityUtils
import com.example.util.SecurityReport
import com.example.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import org.json.JSONArray

class SmsGatewayViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val authManager = AuthManager(context)
    private val repository = SmsRepository(SmsDatabase.getDatabase(context).smsDao())

    // --- ADVANCED MESSAGING STATE FLOWS ---
    val allScheduledSms = repository.allScheduledSms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allDrafts = repository.allDrafts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTemplates = repository.allTemplates.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allContacts = repository.allContacts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Groq TRIAL CONFIGS
    val groqApiKeyInput = MutableStateFlow(authManager.getGroqApiKey())
    val groqModelInput = MutableStateFlow(authManager.getGroqModel())

    // WEBSITE CORE CONFIGS
    val websiteUrlInput = MutableStateFlow(authManager.getWebsiteUrl())
    val websiteConnected = MutableStateFlow(authManager.isWebsiteConnected())
    val websitePublishableKey = MutableStateFlow(authManager.getWebsitePublishableKey())
    val websiteSecretToken = MutableStateFlow(authManager.getWebsiteSecretToken())
    val websitePollingEnabled = MutableStateFlow(authManager.isWebsitePollingEnabled())
    val websitePollingIntervalSec = MutableStateFlow(authManager.getWebsitePollingIntervalSec())

    val allDynamicRows = repository.allDynamicRows.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allHubLogs = repository.allHubLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allNotificationAudits = repository.allNotificationAudits.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // TRANSIENT STATES
    val isImportingContacts = MutableStateFlow(false)
    val aiGeneratingState = MutableStateFlow(false)

    // Form inputs state
    val deviceIdInput = MutableStateFlow(authManager.getDeviceId() ?: "")
    val deviceTokenInput = MutableStateFlow(authManager.getDeviceToken() ?: "")
    val apiUrlInput = MutableStateFlow(authManager.getApiUrl())
    val isTokenVisible = MutableStateFlow(false)

    // Settings States
    val pollIntervalInput = MutableStateFlow(authManager.getPollIntervalSec())
    val heartbeatIntervalInput = MutableStateFlow(authManager.getHeartbeatIntervalSec())
    val autoReconnectEnabled = MutableStateFlow(authManager.isAutoReconnectEnabled())
    val startOnBootEnabled = MutableStateFlow(authManager.isStartOnBootEnabled())
    val gatewayPaused = MutableStateFlow(authManager.isGatewayPaused())

    val persistentNotificationEnabled = MutableStateFlow(authManager.isPersistentNotificationEnabled())
    val smsSentNotificationEnabled = MutableStateFlow(authManager.isSmsSentNotificationEnabled())
    val smsFailedNotificationEnabled = MutableStateFlow(authManager.isSmsFailedNotificationEnabled())
    val incomingSmsNotificationEnabled = MutableStateFlow(authManager.isIncomingSmsNotificationEnabled())

    // Onboarding & Legal States
    val isOnboardingCompleted = MutableStateFlow(authManager.isOnboardingCompleted())
    val areTermsAccepted = MutableStateFlow(authManager.areTermsAccepted())
    val isPrivacyAccepted = MutableStateFlow(authManager.isPrivacyAccepted())
    val isWorkspaceTransitionActive = MutableStateFlow(false)

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

    // --- APP SECURITY SHIELD FLOWS ---
    private val _securityReport = MutableStateFlow<SecurityReport?>(null)
    val securityReport = _securityReport.asStateFlow()

    val antiScreenshotEnabled = MutableStateFlow(authManager.isAntiScreenshotEnabled())
    val selfDefenseModeEnabled = MutableStateFlow(authManager.isSelfDefenseModeEnabled())

    fun refreshSecurityAudit() {
        val report = SecurityUtils.performSecurityAudit(context)
        _securityReport.value = report
    }

    fun toggleAntiScreenshot(enabled: Boolean) {
        authManager.setAntiScreenshotEnabled(enabled)
        antiScreenshotEnabled.value = enabled
    }

    fun toggleSelfDefense(enabled: Boolean) {
        authManager.setSelfDefenseModeEnabled(enabled)
        selfDefenseModeEnabled.value = enabled
        if (enabled) {
            refreshSecurityAudit()
        }
    }

    init {
        // Initial sync of stats and active SIM details
        GatewayService.updateStats(context)
        loadActiveSims()
        // Run security diagnostics
        refreshSecurityAudit()

        // Seed default notification audits if empty
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (repository.allNotificationAudits.first().isEmpty()) {
                    repository.insertNotificationAudit(
                        com.example.data.db.NotificationAuditEntity(
                            category = "CONNECTION",
                            title = "Gateway Live Server",
                            message = "Secure WebSocket tunnels successfully connected to regional hub Node #18 (AWS-London-East)."
                        )
                    )
                    repository.insertNotificationAudit(
                        com.example.data.db.NotificationAuditEntity(
                            category = "SUGGESTION",
                            title = "Optimize Battery Settings",
                            message = "To guarantee 100% dispatch rates under sleep state, disable power savings configurations for the SimGate gateway."
                        )
                    )
                    repository.insertNotificationAudit(
                        com.example.data.db.NotificationAuditEntity(
                            category = "ERROR",
                            title = "Authentication Rejection",
                            message = "Incoming API client request from 182.44.12.18 was rejected (HTTP 401 Unauthorized API key)."
                        )
                    )
                    repository.insertNotificationAudit(
                        com.example.data.db.NotificationAuditEntity(
                            category = "CRASH",
                            title = "Simulated Core Protection",
                            message = "A background process heap expansion was handled gracefully; system memory is stable."
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Notification seeding failed: ${e.message}")
            }
        }
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
                // Perform deep network internet probe beforehand to verify payload transmission
                if (!NetworkUtils.isInternetAvailable(context)) {
                    withContext(Dispatchers.Main) {
                        _isConnecting.value = false
                        _connectionError.value = "No active internet connection. Please verify your mobile data or WiFi has active internet access."
                    }
                    return@launch
                }

                Log.d("ViewModel", "Requesting connect payload at URL: $apiUrl ...")
                // Save credentials beforehand so getService(context) can use the dynamic URL
                authManager.saveCredentials(devId, devToken, apiUrl)
                
                val response = RetrofitClient.getService(context).connectDevice(ConnectRequest(devId, devToken))
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        _isConnecting.value = false
                        // Start the service
                        triggerServiceState(true)
                        
                        // Show "Activating your workspace" animation/loading state
                        isWorkspaceTransitionActive.value = true
                        viewModelScope.launch(Dispatchers.Main) {
                            delay(2500)
                            isWorkspaceTransitionActive.value = false
                            onSuccess()
                        }
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

    fun updatePollInterval(value: Int) {
        authManager.setPollIntervalSec(value)
        pollIntervalInput.value = value
        forceRefreshLoops()
    }

    fun updateHeartbeatInterval(value: Int) {
        authManager.setHeartbeatIntervalSec(value)
        heartbeatIntervalInput.value = value
        forceRefreshLoops()
    }

    fun updateAutoReconnect(enabled: Boolean) {
        authManager.setAutoReconnectEnabled(enabled)
        autoReconnectEnabled.value = enabled
        forceRefreshLoops()
    }

    fun updateStartOnBoot(enabled: Boolean) {
        authManager.setStartOnBootEnabled(enabled)
        startOnBootEnabled.value = enabled
    }

    fun updateGatewayPaused(paused: Boolean) {
        authManager.setGatewayPaused(paused)
        gatewayPaused.value = paused
        forceRefreshLoops()
    }

    fun updatePersistentNotification(enabled: Boolean) {
        authManager.setPersistentNotificationEnabled(enabled)
        persistentNotificationEnabled.value = enabled
        forceRefreshLoops()
    }

    fun updateSmsSentNotification(enabled: Boolean) {
        authManager.setSmsSentNotificationEnabled(enabled)
        smsSentNotificationEnabled.value = enabled
    }

    fun updateSmsFailedNotification(enabled: Boolean) {
        authManager.setSmsFailedNotificationEnabled(enabled)
        smsFailedNotificationEnabled.value = enabled
    }

    fun updateIncomingSmsNotification(enabled: Boolean) {
        authManager.setIncomingSmsNotificationEnabled(enabled)
        incomingSmsNotificationEnabled.value = enabled
    }

    fun updateOnboardingCompleted(completed: Boolean) {
        authManager.setOnboardingCompleted(completed)
        isOnboardingCompleted.value = completed
    }

    fun updateTermsAccepted(accepted: Boolean) {
        authManager.setTermsAccepted(accepted)
        areTermsAccepted.value = accepted
    }

    fun updatePrivacyAccepted(accepted: Boolean) {
        authManager.setPrivacyAccepted(accepted)
        isPrivacyAccepted.value = accepted
    }

    private fun forceRefreshLoops() {
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

    // --- ADVANCED MESSAGING OPERATIONS ---

    fun getMaskedGroqKey(): String {
        val rawKey = authManager.getGroqApiKey()
        if (rawKey.isBlank()) return ""
        if (rawKey.length <= 10) return "••••••••••••"
        return rawKey.take(5) + "••••••••••••" + rawKey.takeLast(4)
    }

    fun updateGroqApiKey(key: String) {
        authManager.setGroqApiKey(key.trim())
        groqApiKeyInput.value = key.trim()
    }

    fun updateGroqModel(model: String) {
        authManager.setGroqModel(model.trim())
        groqModelInput.value = model.trim()
    }

    // --- WEBSITE DATABASE SYNC OPERATIONS ---
    val websiteValidationState = MutableStateFlow<String?>(null) // null = idle, "validating" = busy, "connected" = success, or error string
    val websiteValidationSuggestions = MutableStateFlow<String?>(null)

    fun updateWebsiteUrl(url: String) {
        authManager.setWebsiteUrl(url.trim())
        websiteUrlInput.value = url.trim()
        authManager.setWebsiteConnected(false)
        websiteConnected.value = false
        websiteValidationState.value = null
        websiteValidationSuggestions.value = null
    }

    fun regenerateWebsiteTokens() {
        authManager.regenerateWebsiteKeys()
        websitePublishableKey.value = authManager.getWebsitePublishableKey()
        websiteSecretToken.value = authManager.getWebsiteSecretToken()
        authManager.setWebsiteConnected(false)
        websiteConnected.value = false
        websiteValidationState.value = null
        websiteValidationSuggestions.value = null
    }

    fun validateWebsiteConnection() {
        val url = websiteUrlInput.value
        if (url.isBlank()) {
            websiteValidationState.value = "Error: Website URL is blank"
            websiteValidationSuggestions.value = "Please enter a valid website endpoint URL (e.g., https://my-store.com/api/simgate)"
            return
        }

        websiteValidationState.value = "validating"
        websiteValidationSuggestions.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val testPayload = JSONObject().apply {
                    put("type", "handshake")
                    put("message", "Verify connection from SimGate Gateway app")
                }

                val body = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    testPayload.toString()
                )

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("X-Publishable-Key", websitePublishableKey.value)
                    .addHeader("X-Secret-Token", websiteSecretToken.value)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            authManager.setWebsiteConnected(true)
                            websiteConnected.value = true
                            websiteValidationState.value = "connected"
                            websiteValidationSuggestions.value = "Successfully authenticated and connected!"
                        } else {
                            authManager.setWebsiteConnected(false)
                            websiteConnected.value = false
                            val code = response.code
                            websiteValidationState.value = "Failed: Server returned HTTP status code $code"
                            websiteValidationSuggestions.value = when (code) {
                                401, 403 -> "Unauthorized: Ensure your website's plugin or receiver matches your publishable key and secret token perfectly."
                                404 -> "Endpoint not found: Check if the path part of your URL is misspelled or the handler function is disabled."
                                500, 502, 503, 504 -> "Internal Server Error: The website's server crashed or timed out. Check website logs/PHP error log."
                                else -> "Invalid response code: Make sure the endpoint accepts POST requests and outputs HTTP code 200 on success."
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    authManager.setWebsiteConnected(false)
                    websiteConnected.value = false
                    val errorMsg = e.localizedMessage ?: "Unknown connection error"
                    websiteValidationState.value = "Error: Unable to reach destination server"
                    websiteValidationSuggestions.value = when {
                        errorMsg.contains("SSL", ignoreCase = true) || errorMsg.contains("cert", ignoreCase = true) -> 
                            "SSL validation error: Ensure your website is secured with a valid HTTPS certificate. Android restricts raw non-SSL HTTP calls by default."
                        errorMsg.contains("timeout", ignoreCase = true) -> 
                            "Request Timeout: The website took too long to reply. Ensure the server is online and port 80/443 is clear."
                        errorMsg.contains("resolve", ignoreCase = true) -> 
                            "Host unresolved: Check domain spelling or connectivity status on your Android device."
                        else -> 
                            "Connection refused or unreachable. Suggestion: Verify if your server is running a firewall (e.g. ModSecurity, Cloudflare) blocking incoming POST or custom headers."
                    }
                }
            }
        }
    }

    fun pushRowToWebsite(row: com.example.data.db.DynamicRowEntity, onResult: (Boolean, String) -> Unit) {
        val url = websiteUrlInput.value
        if (url.isBlank() || !websiteConnected.value) {
            onResult(false, "Integration is disconnected or website URL is empty.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val rawData = JSONObject(row.payload)
                val aesKey = authManager.getWebsiteDecryptionKey()
                val encryptedDataStr = com.example.util.AesEncryptionHelper.encrypt(rawData.toString(), aesKey)

                val pushPayload = JSONObject().apply {
                    put("action", "push_row")
                    put("table", row.tableName)
                    put("id", row.itemId)
                    put("data", rawData)
                    put("encrypted_data", encryptedDataStr)
                }

                val body = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    pushPayload.toString()
                )

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("X-Publishable-Key", websitePublishableKey.value)
                    .addHeader("X-Secret-Token", websiteSecretToken.value)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            insertHubLog("INFO", "Forced push sync success for item '${row.itemId}' (Table: ${row.tableName}). Outgoing payload encrypted successfully using AES key.")
                            onResult(true, "Successfully pushed item '${row.itemId}' from table '${row.tableName}' to website!")
                        } else {
                            insertHubLog("ERROR", "Forced push sync failed code ${response.code}: ${response.message}")
                            onResult(false, "Website returned code ${response.code}: ${response.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    insertHubLog("ERROR", "Forced push sync exception: ${e.localizedMessage}")
                    onResult(false, "Connection error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun deleteSyncedRow(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDynamicRow(id)
            insertHubLog("WARNING", "Deleted local backed up row from sqlite.")
        }
    }

    fun clearSyncedTable(table: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearDynamicTable(table)
            insertHubLog("WARNING", "Cleared all rows for database table '$table'.")
        }
    }

    fun clearAllSyncedRows() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllDynamicRows()
            insertHubLog("WARNING", "Nuked all local dynamic database table entries.")
        }
    }

    fun clearHubLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHubLogs()
        }
    }

    fun insertNotificationAudit(category: String, title: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertNotificationAudit(
                com.example.data.db.NotificationAuditEntity(
                    category = category,
                    title = title,
                    message = message
                )
            )
        }
    }

    fun clearNotificationAudits() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearNotificationAudits()
        }
    }

    fun insertHubLog(level: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertHubLog(com.example.data.db.HubEventLogEntity(level = level, message = message))
        }
    }

    fun getWebsiteDecryptionKey(): String {
        return authManager.getWebsiteDecryptionKey()
    }

    fun regenerateWebsiteDecryptionKey() {
        authManager.regenerateWebsiteDecryptionKey()
        insertHubLog("WARNING", "Regenerated Symmetric AES key: ${authManager.getWebsiteDecryptionKey()}")
    }

    fun updateWebsitePollingEnabled(enabled: Boolean) {
        authManager.setWebsitePollingEnabled(enabled)
        websitePollingEnabled.value = enabled
    }

    fun updateWebsitePollingIntervalSec(seconds: Int) {
        authManager.setWebsitePollingIntervalSec(seconds)
        websitePollingIntervalSec.value = seconds
    }

    fun compressImageUri(context: android.content.Context, uri: android.net.Uri): ByteArray? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val maxWidth = 1048
            val maxHeight = 1048
            val width = originalBitmap.width
            val height = originalBitmap.height
            
            val scaledBitmap = if (width > maxWidth || height > maxHeight) {
                val ratio = width.toFloat() / height.toFloat()
                val targetWidth: Int
                val targetHeight: Int
                if (ratio > 1) {
                    targetWidth = maxWidth
                    targetHeight = (maxWidth / ratio).toInt()
                } else {
                    targetHeight = maxHeight
                    targetWidth = (maxHeight * ratio).toInt()
                }
                android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val resultBytes = outputStream.toByteArray()
            outputStream.close()
            
            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()
            
            resultBytes
        } catch (e: Exception) {
            Log.e("ImageCompress", "Error compressing image: ${e.message}")
            null
        }
    }

    fun postImageToWebsiteChunked(
        itemId: String,
        tableName: String,
        imageBytes: ByteArray,
        onResult: (Boolean, String) -> Unit
    ) {
        val url = websiteUrlInput.value
        if (url.isBlank() || !websiteConnected.value) {
            onResult(false, "Integration is disconnected or website URL is empty.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base64String = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
                val chunkSize = 32000
                val totalLength = base64String.length
                val totalChunks = (totalLength + chunkSize - 1) / chunkSize
                val uploadId = java.util.UUID.randomUUID().toString()

                Log.d("ImageUpload", "Uploading base64 string length: $totalLength. Total chunks: $totalChunks. UploadID: $uploadId")

                var success = true
                var errorMsg = ""

                for (chunkIndex in 0 until totalChunks) {
                    val startOffset = chunkIndex * chunkSize
                    val endOffset = (startOffset + chunkSize).coerceAtMost(totalLength)
                    val substring = base64String.substring(startOffset, endOffset)

                    val payload = JSONObject().apply {
                        put("action", "upload_image_chunk")
                        put("item_id", itemId)
                        put("table", tableName)
                        put("upload_id", uploadId)
                        put("chunk_index", chunkIndex)
                        put("total_chunks", totalChunks)
                        put("chunk_data", substring)
                    }

                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val body = okhttp3.RequestBody.create(
                        "application/json; charset=utf-8".toMediaTypeOrNull(),
                        payload.toString()
                    )

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .addHeader("X-Publishable-Key", websitePublishableKey.value)
                        .addHeader("X-Secret-Token", websiteSecretToken.value)
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            success = false
                            errorMsg = "Chunk ${chunkIndex + 1}/$totalChunks failed with code: ${response.code}"
                            return@use
                        }
                    }

                    if (!success) break
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        onResult(true, "Image compressed and chunk-posted successfully! Uploaded in $totalChunks secure parts.")
                    } else {
                        onResult(false, "Failed to upload image. $errorMsg")
                    }
                }
            } catch (e: java.lang.Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Error: ${e.localizedMessage}")
                }
            }
        }
    }

    // Scheduled SMS
    fun addScheduledSms(title: String, messageTemplate: String, recipients: String, scheduleTime: Long, intervalSec: Long = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = com.example.data.db.ScheduledSmsEntity(
                title = title,
                messageTemplate = messageTemplate,
                recipients = recipients,
                scheduleTime = scheduleTime,
                intervalSec = intervalSec,
                isActive = true
            )
            repository.insertScheduledSms(entity)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                triggerServiceState(true)
            }
        }
    }

    fun deleteScheduledSms(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteScheduledSmsById(id)
        }
    }

    fun toggleScheduledSms(id: Long, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateScheduledSmsStatus(id, active)
        }
    }

    // Drafts
    fun addDraft(title: String, message: String, recipients: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = com.example.data.db.SmsDraftEntity(
                title = title,
                message = message,
                recipients = recipients
            )
            repository.insertDraft(entity)
        }
    }

    fun deleteDraft(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDraftById(id)
        }
    }

    // Templates
    fun addTemplate(title: String, messageText: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = com.example.data.db.SmsTemplateEntity(
                title = title,
                messageText = messageText,
                category = category
            )
            repository.insertTemplate(entity)
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTemplateById(id)
        }
    }

    // Sync Phone Contacts
    fun syncPhoneContacts() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isImportingContacts.value = true
            try {
                val list = mutableListOf<com.example.data.db.ContactEntity>()
                val cursor = context.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    var count = 0
                    while (c.moveToNext() && count < 300) {
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else "Contact"
                        val num = if (numIdx >= 0) c.getString(numIdx) else ""
                        if (num.isNotBlank()) {
                            list.add(com.example.data.db.ContactEntity(name = name, phoneNumber = num, source = "PhoneContacts"))
                            count++
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    repository.insertContacts(list)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to sync contacts: ${e.message}")
            } finally {
                delay(1200)
                isImportingContacts.value = false
            }
        }
    }

    // Import Recipients from Historic database logs
    fun importFromHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            isImportingContacts.value = true
            try {
                val currentLogs = repository.allLogs.first()
                if (currentLogs.isNotEmpty()) {
                    val uniqueNumbers = currentLogs.map { it.phoneNumber }.distinct()
                    val list = uniqueNumbers.mapIndexed { idx, num ->
                        val name = if (num.contains("OTP") || num.lowercase().contains("code")) "OTP Receiver" else "Historic Contact ${idx + 1}"
                        com.example.data.db.ContactEntity(name = name, phoneNumber = num, source = "HistoryImport")
                    }
                    if (list.isNotEmpty()) {
                        repository.insertContacts(list)
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to import from history: ${e.message}")
            } finally {
                delay(1500)
                isImportingContacts.value = false
            }
        }
    }

    // Add manual contact and save it
    fun addManualContact(name: String, phoneNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = com.example.data.db.ContactEntity(
                name = name,
                phoneNumber = phoneNumber.trim(),
                source = "ManualInput"
            )
            repository.insertContacts(listOf(entity))
        }
    }

    // Direct Groq Trial Inference implementation (No edge function)
    @Suppress("DEPRECATION")
    fun generateSchedulesWithAi(prompt: String, callback: (String?) -> Unit) {
        val apiKey = authManager.getGroqApiKey()
        if (apiKey.isBlank()) {
            callback("Please paste your Groq API key in Settings first.")
            return
        }
        val model = authManager.getGroqModel()
        aiGeneratingState.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val systemPrompt = """
                    You are an expert AI Scheduler assistant for SimGate gateway app.
                    The user will prompt you with scheduling requirements. Generate 1 or 2 matching SMS Task schedules.
                    Return ONLY a JSON Array containing objects with the following keys, and NO other conversational words or markdown formatting (Do NOT enclose in ```json or any codeblocks, just raw array string):
                    - "title": (String) name of the scheduled message (e.g. "Weekly Gym Reminder")
                    - "message": (String) message content
                    - "recipients": (String) a realistic default phone number (e.g., "254712345678")
                    - "durationSeconds": (Long) delay duration in seconds for this schedule task (e.g. 7200 for 2 hours, 86400 for 1 day)
                """.trimIndent()

                val requestBodyJson = org.json.JSONObject().apply {
                    put("model", model)
                    put("messages", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(org.json.JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.7)
                }

                val body = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    requestBodyJson.toString()
                )

                val request = okhttp3.Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorStr = response.body?.string() ?: "No error body details"
                        Log.e("GroqAPI", "Error status: ${response.code}, details: $errorStr")
                        withContext(Dispatchers.Main) {
                            callback("Groq trial model returned server error: ${response.code}. Details: $errorStr")
                        }
                        return@launch
                    }
                    val respBody = response.body?.string()
                    if (respBody.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            callback("Empty response from Groq trial model")
                        }
                        return@launch
                    }

                    val jsonResp = org.json.JSONObject(respBody)
                    val choices = jsonResp.optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        withContext(Dispatchers.Main) {
                            callback("No choices returned from Groq")
                        }
                        return@launch
                    }

                    var aiContent = choices.getJSONObject(0).getJSONObject("message").optString("content") ?: ""
                    if (aiContent.contains("```")) {
                        aiContent = aiContent.substringAfter("```json")
                            .substringAfter("```")
                            .substringBefore("```")
                    }
                    aiContent = aiContent.trim()

                    try {
                        val arr = org.json.JSONArray(aiContent)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val title = obj.optString("title", "AI Scheduled Message")
                            val msg = obj.optString("message", "")
                            val rec = obj.optString("recipients", "")
                            val dur = obj.optLong("durationSeconds", 3600)
                            
                            val schedTime = System.currentTimeMillis() + (dur * 1000)
                            val entity = com.example.data.db.ScheduledSmsEntity(
                                title = title,
                                messageTemplate = msg,
                                recipients = rec,
                                scheduleTime = schedTime,
                                intervalSec = 0,
                                isActive = true
                            )
                            repository.insertScheduledSms(entity)
                        }
                        withContext(Dispatchers.Main) {
                            callback(null)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            callback("Could not parse schedule plan: ${e.message}. Content was: $aiContent")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback("Error: ${e.message}")
                }
            } finally {
                aiGeneratingState.value = false
            }
        }
    }

    // --- AUTO REPLY HUB GETTERS & SETTERS ---
    fun isNotificationListenerPermissionGranted(): Boolean {
        val pkgName = getApplication<Application>().packageName
        val flat = android.provider.Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            "enabled_notification_listeners"
        )
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                val cn = android.content.ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }

    fun getAutoReplyEnabled(): Boolean = authManager.isAutoReplyEnabled()
    fun setAutoReplyEnabled(enabled: Boolean) = authManager.setAutoReplyEnabled(enabled)

    fun getAutoReplyPlatforms(): String = authManager.getAutoReplyPlatforms()
    fun setAutoReplyPlatforms(platforms: String) = authManager.setAutoReplyPlatforms(platforms)

    fun getAutoReplyRules(): String = authManager.getAutoReplyRules()
    fun setAutoReplyRules(rulesJson: String) = authManager.setAutoReplyRules(rulesJson)

    fun getAutoReplyMenus(): String = authManager.getAutoReplyMenus()
    fun setAutoReplyMenus(menusJson: String) = authManager.setAutoReplyMenus(menusJson)

    fun getAutoReplyForms(): String = authManager.getAutoReplyForms()
    fun setAutoReplyForms(formsJson: String) = authManager.setAutoReplyForms(formsJson)

    fun getAutoReplyFallback(): String = authManager.getAutoReplyFallbackReplies()
    fun setAutoReplyFallback(fallbacks: String) = authManager.setAutoReplyFallbackReplies(fallbacks)

    fun getAutoReplyWelcomeMsg(): String = authManager.getAutoReplyWelcomeMessage()
    fun setAutoReplyWelcomeMsg(msg: String) = authManager.setAutoReplyWelcomeMessage(msg)

    fun isAutoReplyWelcomeActive(): Boolean = authManager.isAutoReplyWelcomeActive()
    fun setAutoReplyWelcomeActive(active: Boolean) = authManager.setAutoReplyWelcomeActive(active)

    fun isAutoReplyAiActive(): Boolean = authManager.isAutoReplyAiActive()
    fun setAutoReplyAiActive(active: Boolean) = authManager.setAutoReplyAiActive(active)

    fun getAutoReplyAiInstructions(): String = authManager.getAutoReplyAiInstructions()
    fun setAutoReplyAiInstructions(instructions: String) = authManager.setAutoReplyAiInstructions(instructions)

    fun getAutoReplyStartHour(): Int = authManager.getAutoReplyStartHour()
    fun setAutoReplyStartHour(hour: Int) = authManager.setAutoReplyStartHour(hour)

    fun getAutoReplyEndHour(): Int = authManager.getAutoReplyEndHour()
    fun setAutoReplyEndHour(hour: Int) = authManager.setAutoReplyEndHour(hour)

    fun isAutoReplyScheduleActive(): Boolean = authManager.isAutoReplyScheduleActive()
    fun setAutoReplyScheduleActive(active: Boolean) = authManager.setAutoReplyScheduleActive(active)

    fun getAutoReplyDelaySeconds(): Int = authManager.getAutoReplyDelaySeconds()
    fun setAutoReplyDelaySeconds(seconds: Int) = authManager.setAutoReplyDelaySeconds(seconds)

    // Stats Retrieval
    fun getAutoReplySentCount(): Int = authManager.getAutoReplySentCount()
    fun getAutoReplyUniqueContactsCount(): Int {
        val list = authManager.getAutoReplyUniqueContacts().split(",").filter { it.isNotBlank() }
        return list.size
    }
    fun getAutoReplyMatchRate(): Float = authManager.getAutoReplyMatchRate()
    fun getAutoReplyLastTriggerTime(): Long = authManager.getAutoReplyLastTriggerTime()

    fun resetAutoReplyStats() {
        authManager.setAutoReplySentCount(0)
        authManager.setAutoReplyUniqueContacts("")
        authManager.setAutoReplyMatchRate(100.0f)
        authManager.setAutoReplyLastTriggerTime(0L)
    }

    fun isSchedulePromoShown(): Boolean = authManager.isSchedulePromoShown()
    fun setSchedulePromoShown(shown: Boolean) = authManager.setSchedulePromoShown(shown)

    fun getFormCollectionTarget(): String = authManager.getFormCollectionTarget()
    fun setFormCollectionTarget(target: String) = authManager.setFormCollectionTarget(target)

    fun getInputTargetUrl(): String = authManager.getInputTargetUrl()
    fun setInputTargetUrl(url: String) = authManager.setInputTargetUrl(url)

    fun getCollectedFormsSubmissions(): String = authManager.getCollectedFormsSubmissions()
    fun clearAllCollectedSubmissions() = authManager.clearAllCollectedSubmissions()
}

fun maskPhoneNumber(phoneNumber: String): String {
    val clean = phoneNumber.trim()
    if (clean.length < 5) return clean
    return if (clean.length >= 7) {
        clean.take(3) + "****" + clean.takeLast(3)
    } else {
        clean.take(2) + "***" + clean.takeLast(2)
    }
}
