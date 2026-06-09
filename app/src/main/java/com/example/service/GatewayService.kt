package com.example.service

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.api.ConnectRequest
import com.example.data.api.HeartbeatRequest
import com.example.data.api.IncomingSmsRequest
import com.example.data.api.PollRequest
import com.example.data.api.ReportFailedRequest
import com.example.data.api.ReportSentRequest
import com.example.data.api.RetrofitClient
import com.example.data.api.SmsJob
import com.example.data.db.SmsDatabase
import com.example.data.db.SmsEntity
import com.example.data.db.SmsRepository
import com.example.data.pref.AuthManager
import com.example.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.*

class GatewayService : Service() {

    companion object {
        private const val TAG = "GatewayService"
        private const val NOTIFICATION_ID = 8821
        private const val CHANNEL_ID = "SMS_GATEWAY_CHANNEL"

        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"

        // Global state observable by UI ViewModels
        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceStateFlow = _serviceState.asStateFlow()

        fun updateStats(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = SmsDatabase.getDatabase(context)
                val repo = SmsRepository(db.smsDao())
                val sent = repo.getSentTodayCount()
                val failed = repo.getFailedTodayCount()
                val received = repo.getReceivedTodayCount()
                _serviceState.value = _serviceState.value.copy(
                    sentToday = sent,
                    failedToday = failed,
                    receivedToday = received
                )
            }
        }
    }

    data class ServiceState(
        val isRunning: Boolean = false,
        val isConnected: Boolean = false,
        val connectionMessage: String = "Disconnected",
        val lastHeartbeatTime: String = "Never",
        val batteryPct: Int = 100,
        val signalLevel: Int = 4, // 0-4 scale
        val signalText: String = "Strong",
        val activeSimInfo: String = "Default SIM",
        val sentToday: Int = 0,
        val failedToday: Int = 0,
        val receivedToday: Int = 0,
        val uptimeString: String = "0h 0m"
    )

    private val binder = GatewayBinder()
    private var serviceJob = Job()
    private var serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var authManager: AuthManager
    private lateinit var repository: SmsRepository
    private var startTimeMillis: Long = 0

    // Timing configurations
    private val pollIntervalMs = 5000L
    private val heartbeatIntervalMs = 60000L

    // For exponential backoff in reconnection
    private val retryBackoffs = listOf(5000L, 10000L, 20000L, 30000L)
    private var retryIndex = 0

    private var pollJob: Job? = null
    private var heartbeatJob: Job? = null
    private var statusUpdateJob: Job? = null
    private var scheduledSmsJob: Job? = null
    private var websitePollJob: Job? = null
    private var localHttpServer: com.sun.net.httpserver.HttpServer? = null

    // Broadcaster list of registered SMS receivers
    private val smsSentReceivers = mutableMapOf<String, BroadcastReceiver>()

    inner class GatewayBinder : Binder() {
        fun getService(): GatewayService = this@GatewayService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        authManager = AuthManager(this)
        val db = SmsDatabase.getDatabase(this)
        repository = SmsRepository(db.smsDao())
        startTimeMillis = System.currentTimeMillis()

        registerBatteryReceiver()
        registerSignalStrength()
        createNotificationChannel()

        updateDynamicSimStatus()
        updateUptime()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")
        when (action) {
            ACTION_START -> {
                startGatewayService()
            }
            ACTION_STOP -> {
                stopGatewayService()
            }
            else -> {
                startGatewayService()
            }
        }
        return START_STICKY
    }

    private fun startGatewayService() {
        if (_serviceState.value.isRunning) {
            Log.d(TAG, "Service is already running.")
            return
        }

        val hasCreds = authManager.hasCredentials()

        authManager.setGatewayRunning(true)
        _serviceState.value = _serviceState.value.copy(
            isRunning = true,
            connectionMessage = if (hasCreds) "Reconnecting..." else "Local-Only Scheduler Active"
        )

        // Reset tracking jobs
        serviceJob.cancel()
        serviceJob = Job()
        serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

        // Wake Lock to keep running when minimized/screen off
        acquireWakeLock()

        // Core continuous Loops
        startStatusUpdating()
        startActiveLoops()

        // Refresh dynamic SIM options
        updateDynamicSimStatus()

        // Create and start foreground with notification
        val notification = buildServiceNotification(if (hasCreds) "Connecting to server..." else "Local-Only Scheduler Active")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Perform initial connect call if credentials exist
        if (hasCreds) {
            connectToServer()
        } else {
            Log.i(TAG, "Gateway running in Local-Only Standalone mode. Server connection skipped.")
        }

        // Start Standalone Website sync HTTP server
        startLocalHttpServer()
        startWebsitePolling()
    }

    private fun stopGatewayService() {
        Log.d(TAG, "Stopping service")
        authManager.setGatewayRunning(false)

        pollJob?.cancel()
        heartbeatJob?.cancel()
        statusUpdateJob?.cancel()
        scheduledSmsJob?.cancel()
        websitePollJob?.cancel()
        serviceJob.cancel()

        // Unregister any active sent receivers
        smsSentReceivers.forEach { (jobId, receiver) ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
        smsSentReceivers.clear()

        _serviceState.value = _serviceState.value.copy(
            isRunning = false,
            isConnected = false,
            connectionMessage = "Stopped"
        )

        stopForeground(true)
        stopLocalHttpServer()
        stopSelf()
    }

    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsGateway::ServiceWakeLock").apply {
                acquire(10 * 60 * 1000L /*10 mins fallback*/)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock: ${e.message}")
        }
    }

    private fun startStatusUpdating() {
        statusUpdateJob = serviceScope.launch {
            while (isActive) {
                updateUptime()
                updateBatteryAndSignalState()
                updateStats(this@GatewayService)
                delay(10000L) // Update counts/uptime every 10 seconds
            }
        }
    }

    private fun updateUptime() {
        val diff = System.currentTimeMillis() - startTimeMillis
        val hours = diff / (3600000)
        val minutes = (diff % 3600000) / 60000
        val uptime = "${hours}h ${minutes}m"
        _serviceState.value = _serviceState.value.copy(uptimeString = uptime)
    }

    private fun startActiveLoops() {
        // Heartbeat timer every 60s (or custom dynamic interval)
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                val intervalMs = authManager.getHeartbeatIntervalSec() * 1000L
                if (_serviceState.value.isConnected && !authManager.isGatewayPaused()) {
                    sendHeartbeatToServer()
                }
                delay(intervalMs)
            }
        }

        // Job Polling timer with backoff on reconnect
        pollJob = serviceScope.launch {
            while (isActive) {
                val intervalMs = authManager.getPollIntervalSec() * 1000L
                if (authManager.isGatewayPaused()) {
                    _serviceState.value = _serviceState.value.copy(
                        connectionMessage = "Paused"
                    )
                    delay(intervalMs)
                } else if (_serviceState.value.isConnected) {
                    pollJobsFromServer()
                    delay(intervalMs)
                } else {
                    // Reconnect attempt if disconnected with backoff
                    connectToServer()
                    val backoff = retryBackoffs.getOrNull(retryIndex) ?: 5000L
                    delay(backoff)
                }
            }
        }

        // Continuous exact timer for local scheduled SMS (every 1 second)
        scheduledSmsJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAndProcessScheduledSms()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking/sending scheduled SMS: ${e.message}")
                }
                delay(1000L)
            }
        }
    }

    private fun connectToServer() {
        val devId = authManager.getDeviceId() ?: return
        val devToken = authManager.getDeviceToken() ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (!NetworkUtils.isInternetAvailable(this@GatewayService)) {
                    handleConnectionFailure("No internet / offline")
                    return@launch
                }
                Log.d(TAG, "Connecting to sever for validation...")
                val response = RetrofitClient.getService(this@GatewayService).connectDevice(ConnectRequest(devId, devToken))
                if (response.isSuccessful) {
                    serviceScope.launch(Dispatchers.IO) {
                        repository.insertNotificationAudit(
                            com.example.data.db.NotificationAuditEntity(
                                category = "CONNECTION",
                                title = "Gateway Connected",
                                message = "Secure connection verified with target dispatcher API server."
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        retryIndex = 0 // Reset backoff levels
                        if (!_serviceState.value.isConnected) {
                            _serviceState.value = _serviceState.value.copy(
                                isConnected = true,
                                connectionMessage = "Connected"
                            )
                            updateNotification("Active and running")
                            // Send positive response heartbeat
                            sendHeartbeatToServer()
                        }
                    }
                } else {
                    handleConnectionFailure("API returned error ${response.code()}")
                }
            } catch (e: Exception) {
                handleConnectionFailure(e.localizedMessage ?: "Unknown network error")
            }
        }
    }

    private fun handleConnectionFailure(reason: String) {
        serviceScope.launch(Dispatchers.IO) {
            repository.insertNotificationAudit(
                com.example.data.db.NotificationAuditEntity(
                    category = "CONNECTION",
                    title = "Connection Interrupted",
                    message = "Handshake failed or lost connectivity: $reason. Backoff retry scheduled."
                )
            )
        }
        serviceScope.launch(Dispatchers.Main) {
            _serviceState.value = _serviceState.value.copy(
                isConnected = false,
                connectionMessage = "Reconnecting: $reason"
            )
            updateNotification("Reconnecting... $reason")

            // Backoff logic
            val delayMs = retryBackoffs[retryIndex]
            Log.w(TAG, "Connection failed ($reason). Retrying in ${delayMs / 1000}s...")
            retryIndex = (retryIndex + 1).coerceAtMost(retryBackoffs.lastIndex)
        }
    }

    private fun sendHeartbeatToServer() {
        val devId = authManager.getDeviceId() ?: return
        val devToken = authManager.getDeviceToken() ?: return
        val battery = _serviceState.value.batteryPct
        val signal = _serviceState.value.signalLevel

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (!NetworkUtils.isInternetAvailable(this@GatewayService)) {
                    Log.w(TAG, "Heartbeat aborted: Offline (no active internet).")
                    withContext(Dispatchers.Main) {
                        _serviceState.value = _serviceState.value.copy(
                            isConnected = false,
                            connectionMessage = "Offline (no active internet)"
                        )
                        updateNotification("Offline (no active internet)")
                    }
                    return@launch
                }
                Log.d(TAG, "Sending heartbeat to server...")
                val response = RetrofitClient.getService(this@GatewayService).sendHeartbeat(
                    HeartbeatRequest(devId, devToken, battery, signal)
                )
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        val formattedTime = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
                        _serviceState.value = _serviceState.value.copy(
                            lastHeartbeatTime = formattedTime,
                            isConnected = true,
                            connectionMessage = "Connected"
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _serviceState.value = _serviceState.value.copy(isConnected = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat error: ${e.message}")
            }
        }
    }

    private fun pollJobsFromServer() {
        val devId = authManager.getDeviceId() ?: return
        val devToken = authManager.getDeviceToken() ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (!NetworkUtils.isInternetAvailable(this@GatewayService)) {
                    Log.w(TAG, "Polling aborted: Offline (no active internet).")
                    withContext(Dispatchers.Main) {
                        _serviceState.value = _serviceState.value.copy(
                            isConnected = false,
                            connectionMessage = "Offline"
                        )
                    }
                    return@launch
                }
                Log.d(TAG, "Polling jobs...")
                val response = RetrofitClient.getService(this@GatewayService).pollJobs(PollRequest(devId, devToken))
                if (response.isSuccessful) {
                    val body = response.body()
                    val jobs = body?.jobs
                    if (!jobs.isNullOrEmpty()) {
                        Log.i(TAG, "Received ${jobs.size} jobs to process!")
                        jobs.forEach { job ->
                            if (job.type == "send_sms") {
                                processSmsJob(job)
                            } else {
                                Log.w(TAG, "Unknown job type: ${job.type}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to poll jobs: ${e.message}")
            }
        }
    }

    private fun processSmsJob(job: SmsJob) {
        serviceScope.launch(Dispatchers.Default) {
            // Find appropriate SmsManager/SIM ID
            val activeSimPref = authManager.getSelectedSim()
            var usedSimDetails = "Default SIM"
            var subId = -1

            if (ContextCompat.checkSelfPermission(
                    this@GatewayService,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val subManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val list = subManager.activeSubscriptionInfoList
                if (!list.isNullOrEmpty()) {
                    if (activeSimPref.startsWith("SIM 1") && list.size > 0) {
                        val subInfo = list.find { it.simSlotIndex == 0 } ?: list[0]
                        subId = subInfo.subscriptionId
                        usedSimDetails = "SIM 1 (${subInfo.carrierName})"
                    } else if (activeSimPref.startsWith("SIM 2") && list.size > 1) {
                        val subInfo = list.find { it.simSlotIndex == 1 } ?: list.getOrNull(1) ?: list[0]
                        subId = subInfo.subscriptionId
                        usedSimDetails = "SIM 2 (${subInfo.carrierName})"
                    } else {
                        // "Auto" or "Default"
                        // Pick default SMS SIM subscription ID from the system
                        val defaultSmsId = SubscriptionManager.getDefaultSmsSubscriptionId()
                        val subInfo = list.find { it.subscriptionId == defaultSmsId } ?: list[0]
                        subId = subInfo.subscriptionId
                        usedSimDetails = "Auto [SIM ${subInfo.simSlotIndex + 1} (${subInfo.carrierName})]"
                    }
                }
            }

            try {
                // Initialize SmsManager
                val smsManager = if (subId != -1) {
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    SmsManager.getDefault()
                }

                // Prepare tracking intents
                val sentAction = "SMS_SENT_${job.jobId}"
                
                // Set up receiver dynamically
                val sentReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        unregisterSmsReceiver(job.jobId)
                        val code = resultCode
                        if (code == Activity.RESULT_OK) {
                            reportSuccess(job, usedSimDetails)
                        } else {
                            val errorReason = when (code) {
                                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                                SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service"
                                SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                                SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio turned off"
                                else -> "Error code $code"
                            }
                            reportFailure(job, errorReason, usedSimDetails)
                        }
                    }
                }

                registerSmsReceiver(job.jobId, sentReceiver, sentAction)

                val sentPI = PendingIntent.getBroadcast(
                    this@GatewayService,
                    0,
                    Intent(sentAction).apply { `package` = packageName },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                Log.d(TAG, "Sending SMS to: ${job.recipient} on $usedSimDetails...")
                val parts = smsManager.divideMessage(job.message)
                if (parts.size > 1) {
                    val sentIntents = ArrayList<PendingIntent>().apply { add(sentPI) }
                    smsManager.sendMultipartTextMessage(job.recipient, null, parts, sentIntents, null)
                } else {
                    smsManager.sendTextMessage(job.recipient, null, job.message, sentPI, null)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception in SMS dispatch: ${e.message}")
                reportFailure(job, e.localizedMessage ?: "Device driver execution exception", usedSimDetails)
            }
        }
    }

    @Synchronized
    private fun registerSmsReceiver(jobId: String, receiver: BroadcastReceiver, action: String) {
        smsSentReceivers[jobId] = receiver
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    @Synchronized
    private fun unregisterSmsReceiver(jobId: String) {
        val receiver = smsSentReceivers.remove(jobId)
        if (receiver != null) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
    }

    private fun reportSuccess(job: SmsJob, simDetails: String) {
        if (authManager.isSmsSentNotificationEnabled()) {
            showLocalNotification(
                title = "SMS Sent Successfully",
                body = "To: ${job.recipient}\nMessage: ${job.message}",
                isSuccess = true
            )
        }

        val devId = authManager.getDeviceId() ?: return
        val devToken = authManager.getDeviceToken() ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Log to Local database first
                repository.insertLog(
                    SmsEntity(
                        type = "Sent",
                        phoneNumber = job.recipient,
                        message = job.message,
                        status = "Sent",
                        simUsed = simDetails
                    )
                )
                updateStats(this@GatewayService)

                // Report back to central server
                Log.d(TAG, "Reporting success for job: ${job.jobId}")
                if (NetworkUtils.isInternetAvailable(this@GatewayService)) {
                    RetrofitClient.getService(this@GatewayService).reportSent(ReportSentRequest(devId, devToken, job.jobId))
                } else {
                    Log.w(TAG, "Unable to report success for job: ${job.jobId} - Offline (no active internet)")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error reporting SMS success: ${e.message}")
            }
        }
    }

    private fun reportFailure(job: SmsJob, reason: String, simDetails: String) {
        if (authManager.isSmsFailedNotificationEnabled()) {
            showLocalNotification(
                title = "SMS Delivery Failed",
                body = "To: ${job.recipient}\nReason: $reason\nMessage: ${job.message}",
                isSuccess = false
            )
        }

        val devId = authManager.getDeviceId() ?: return
        val devToken = authManager.getDeviceToken() ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Log to Local database first
                repository.insertLog(
                    SmsEntity(
                        type = "Failed",
                        phoneNumber = job.recipient,
                        message = job.message,
                        status = "Failed",
                        simUsed = simDetails,
                        errorMessage = reason
                    )
                )
                updateStats(this@GatewayService)

                // Report back failing status to API
                Log.d(TAG, "Reporting failure for job: ${job.jobId}, reason: $reason")
                if (NetworkUtils.isInternetAvailable(this@GatewayService)) {
                    RetrofitClient.getService(this@GatewayService).reportFailed(ReportFailedRequest(devId, devToken, job.jobId, reason))
                } else {
                    Log.w(TAG, "Unable to report failure for job: ${job.jobId} - Offline (no active internet)")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error reporting SMS failure: ${e.message}")
            }
        }
    }

    // Battery Broadcast Receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    val pct = (level * 100 / scale.toFloat()).toInt()
                    _serviceState.value = _serviceState.value.copy(batteryPct = pct)
                }
            }
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
    }

    private fun registerSignalStrength() {
        // Simplified dynamic signal strength reader
        // For production simplicity we poll signal state, but we can set defaults
        serviceScope.launch {
            while (isActive) {
                updateBatteryAndSignalState()
                delay(30000L)
            }
        }
    }

    private fun updateBatteryAndSignalState() {
        try {
            // Read battery manually as fallback
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct > 0) {
                _serviceState.value = _serviceState.value.copy(batteryPct = pct)
            }

            // Read cellular signal dynamically
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val signal = tm.signalStrength
                    signal?.let {
                        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            it.level
                        } else {
                            4 // fallback to strong
                        }
                        val signalText = when (level) {
                            0 -> "No Service"
                            1, 2 -> "Weak"
                            3 -> "Moderate"
                            else -> "Strong"
                        }
                        _serviceState.value = _serviceState.value.copy(
                            signalLevel = level,
                            signalText = "$level/4 ($signalText)"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read battery/signal: ${e.message}")
        }
    }

    private fun updateDynamicSimStatus() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _serviceState.value = _serviceState.value.copy(activeSimInfo = "Permission missing")
            return
        }

        try {
            val subManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeList = subManager.activeSubscriptionInfoList
            if (activeList.isNullOrEmpty()) {
                _serviceState.value = _serviceState.value.copy(activeSimInfo = "No SIM Cards Active")
            } else {
                val details = activeList.joinToString(" / ") { info ->
                    "SIM ${info.simSlotIndex + 1} (${info.carrierName})"
                }
                _serviceState.value = _serviceState.value.copy(activeSimInfo = details)
            }
        } catch (e: Exception) {
            _serviceState.value = _serviceState.value.copy(activeSimInfo = "SIM manager error")
        }
    }

    // Foreground Notification Methods

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Gateway Continuous Notification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that SMS Gateway background sync and polling is fully operational"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildServiceNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPI = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop button trigger
        val stopIntent = Intent(this, GatewayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPI = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelDescription = if (_serviceState.value.isConnected) "Connected • SMS Gateway Online" else "Offline • SMS Gateway Reconnecting"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Using actual system app icon
            .setContentTitle("SMS Gateway Running")
            .setContentText(contentText)
            .setSubText(channelDescription)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPI)
            .addAction(R.drawable.ic_launcher_foreground, "Open App", openAppPI) // Using foreground icon placeholder helper
            .addAction(R.drawable.ic_launcher_background, "Stop Service", stopPI)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildServiceNotification(text))
    }

    private suspend fun checkAndProcessScheduledSms() {
        val list = repository.allScheduledSms.first()
        val now = System.currentTimeMillis()
        list.forEach { task ->
            if (task.isActive && task.scheduleTime <= now) {
                Log.i(TAG, "Found active overdue scheduled task (ID: ${task.id}, title: ${task.title}, time: ${task.scheduleTime})")
                // Reschedule if interval is set, otherwise mark inactive
                if (task.intervalSec > 0) {
                    val nextTime = now + (task.intervalSec * 1000L)
                    val rescheduledTask = task.copy(scheduleTime = nextTime, isActive = true)
                    repository.insertScheduledSms(rescheduledTask)
                } else {
                    repository.updateScheduledSmsStatus(task.id, false)
                }
                // Send it!
                sendScheduledSms(task)
            }
        }
    }

    private fun sendScheduledSms(task: com.example.data.db.ScheduledSmsEntity) {
        serviceScope.launch(Dispatchers.Default) {
            // Find appropriate SmsManager/SIM ID
            val activeSimPref = authManager.getSelectedSim()
            var usedSimDetails = "Default SIM"
            var subId = -1

            if (ContextCompat.checkSelfPermission(
                    this@GatewayService,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val subManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val list = subManager.activeSubscriptionInfoList
                if (!list.isNullOrEmpty()) {
                    if (activeSimPref.startsWith("SIM 1") && list.size > 0) {
                        val subInfo = list.find { it.simSlotIndex == 0 } ?: list[0]
                        subId = subInfo.subscriptionId
                        usedSimDetails = "SIM 1 (${subInfo.carrierName})"
                    } else if (activeSimPref.startsWith("SIM 2") && list.size > 1) {
                        val subInfo = list.find { it.simSlotIndex == 1 } ?: list.getOrNull(1) ?: list[0]
                        subId = subInfo.subscriptionId
                        usedSimDetails = "SIM 2 (${subInfo.carrierName})"
                    } else {
                        // "Auto" or "Default"
                        val defaultSmsId = SubscriptionManager.getDefaultSmsSubscriptionId()
                        val subInfo = list.find { it.subscriptionId == defaultSmsId } ?: list[0]
                        subId = subInfo.subscriptionId
                        usedSimDetails = "Auto [SIM ${subInfo.simSlotIndex + 1} (${subInfo.carrierName})]"
                    }
                }
            }

            // Split comma-separated recipients
            val phoneList = task.recipients.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (phoneList.isEmpty()) {
                Log.w(TAG, "No valid recipients for scheduled task: ${task.title}")
                return@launch
            }

            try {
                // Initialize SmsManager
                val smsManager = if (subId != -1) {
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    SmsManager.getDefault()
                }

                phoneList.forEach { phone ->
                    // Prepare tracking intents or use local log
                    val sentAction = "SMS_SCHEDULED_SENT_${task.id}_${System.currentTimeMillis()}"
                    
                    // Set up receiver dynamically
                    val sentReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            try {
                                unregisterReceiver(this)
                            } catch (e: Exception) {
                                // Ignore
                            }
                            val code = resultCode
                            if (code == Activity.RESULT_OK) {
                                Log.i(TAG, "Scheduled SMS sent successfully to $phone")
                                if (authManager.isSmsSentNotificationEnabled()) {
                                    showLocalNotification(
                                        title = "Scheduled SMS Sent",
                                        body = "To: $phone\nMessage: ${task.messageTemplate}",
                                        isSuccess = true
                                    )
                                }
                                serviceScope.launch(Dispatchers.IO) {
                                    repository.insertLog(
                                        SmsEntity(
                                            type = "Sent",
                                            phoneNumber = phone,
                                            message = task.messageTemplate,
                                            status = "Sent",
                                            simUsed = usedSimDetails
                                        )
                                    )
                                    updateStats(this@GatewayService)
                                }
                            } else {
                                val errorReason = when (code) {
                                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                                    SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service"
                                    SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio turned off"
                                    else -> "Error code $code"
                                }
                                Log.e(TAG, "Scheduled SMS failed to $phone: $errorReason")
                                if (authManager.isSmsFailedNotificationEnabled()) {
                                    showLocalNotification(
                                        title = "Scheduled SMS Failed",
                                        body = "To: $phone\nReason: $errorReason\nMessage: ${task.messageTemplate}",
                                        isSuccess = false
                                    )
                                }
                                serviceScope.launch(Dispatchers.IO) {
                                    repository.insertLog(
                                        SmsEntity(
                                            type = "Failed",
                                            phoneNumber = phone,
                                            message = task.messageTemplate,
                                            status = "Failed",
                                            simUsed = usedSimDetails,
                                            errorMessage = errorReason
                                        )
                                    )
                                    updateStats(this@GatewayService)
                                }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(sentReceiver, IntentFilter(sentAction), Context.RECEIVER_EXPORTED)
                    } else {
                        registerReceiver(sentReceiver, IntentFilter(sentAction))
                    }

                    val sentPI = PendingIntent.getBroadcast(
                        this@GatewayService,
                        0,
                        Intent(sentAction).apply { `package` = packageName },
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    Log.d(TAG, "Sending scheduled SMS to: $phone on $usedSimDetails...")
                    val parts = smsManager.divideMessage(task.messageTemplate)
                    if (parts.size > 1) {
                        val sentIntents = ArrayList<PendingIntent>().apply { add(sentPI) }
                        smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
                    } else {
                        smsManager.sendTextMessage(phone, null, task.messageTemplate, sentPI, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in scheduled SMS dispatch: ${e.message}")
                phoneList.forEach { phone ->
                    if (authManager.isSmsFailedNotificationEnabled()) {
                        showLocalNotification(
                            title = "Scheduled SMS Failed",
                            body = "To: $phone\nReason: ${e.localizedMessage ?: "Exception"}\nMessage: ${task.messageTemplate}",
                            isSuccess = false
                        )
                    }
                    serviceScope.launch(Dispatchers.IO) {
                        repository.insertLog(
                            SmsEntity(
                                type = "Failed",
                                phoneNumber = phone,
                                message = task.messageTemplate,
                                status = "Failed",
                                simUsed = usedSimDetails,
                                errorMessage = e.localizedMessage ?: "Device driver execution exception"
                            )
                        )
                        updateStats(this@GatewayService)
                    }
                }
            }
        }
    }

    private fun startLocalHttpServer() {
        try {
            stopLocalHttpServer()
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(8085), 0)
            server.createContext("/api/sync") { exchange ->
                var responseCode = 200
                var responseText = ""
                try {
                    val method = exchange.requestMethod
                    val authHeaderKey = exchange.requestHeaders.getFirst("X-Publishable-Key") ?: ""
                    val authHeaderToken = exchange.requestHeaders.getFirst("X-Secret-Token") ?: ""

                    val localPk = authManager.getWebsitePublishableKey()
                    val localSk = authManager.getWebsiteSecretToken()

                    if (authHeaderKey != localPk || authHeaderToken != localSk) {
                        responseCode = 401
                        responseText = "{\"success\":false,\"error\":\"Unauthorized credentials mapping failed.\"}"
                    } else if (method == "POST") {
                        val body = exchange.requestBody.bufferedReader().use { it.readText() }
                        var json = JSONObject(body)
                        val aesKey = authManager.getWebsiteDecryptionKey()
                        
                        if (json.has("encrypted_data")) {
                            try {
                                val encStr = json.getString("encrypted_data")
                                val decStr = com.example.util.AesEncryptionHelper.decrypt(encStr, aesKey)
                                if (decStr != encStr && decStr.startsWith("{")) {
                                    json = JSONObject(decStr)
                                    serviceScope.launch(Dispatchers.IO) {
                                        repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                            level = "INFO",
                                            message = "Received encrypted payload. Successfully decrypted using symmetric AES key."
                                        ))
                                    }
                                }
                            } catch (e: Exception) {
                                serviceScope.launch(Dispatchers.IO) {
                                    repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                        level = "ERROR",
                                        message = "Failed to decrypt incoming payload: ${e.message}"
                                    ))
                                }
                            }
                        }

                        val table = json.optString("table", "unknown")
                        serviceScope.launch(Dispatchers.IO) {
                            repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                level = "INFO",
                                message = "HTTP POST sync request received for local table '$table'."
                            ))
                        }
                        
                        if (json.has("data")) {
                            val dataObj = json.get("data")
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    if (dataObj is JSONArray) {
                                        for (i in 0 until dataObj.length()) {
                                            val rowItem = dataObj.getJSONObject(i)
                                            val idStr = if (rowItem.has("id")) rowItem.get("id").toString() else UUID.randomUUID().toString()
                                            
                                            // Process sensitivities (Hash + images download)
                                            val hashed = com.example.util.SmsGatewaySecurityAndSyncUtils.hashSensitiveDataInPayload(rowItem.toString())
                                            val localized = com.example.util.SmsGatewaySecurityAndSyncUtils.downloadAndLocalizeImagesInPayload(applicationContext, hashed, repository)
                                            
                                            repository.insertDynamicRow(
                                                com.example.data.db.DynamicRowEntity(
                                                    tableName = table,
                                                    itemId = idStr,
                                                    payload = localized
                                                )
                                            )
                                        }
                                        repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                            level = "INFO",
                                            message = "Sync success: Ingested and secured ${dataObj.length()} items into table '$table'."
                                        ))
                                    } else if (dataObj is JSONObject) {
                                        val idStr = if (dataObj.has("id")) dataObj.get("id").toString() else UUID.randomUUID().toString()
                                        
                                        // Process sensitivities (Hash + images download)
                                        val hashed = com.example.util.SmsGatewaySecurityAndSyncUtils.hashSensitiveDataInPayload(dataObj.toString())
                                        val localized = com.example.util.SmsGatewaySecurityAndSyncUtils.downloadAndLocalizeImagesInPayload(applicationContext, hashed, repository)
                                        
                                        repository.insertDynamicRow(
                                            com.example.data.db.DynamicRowEntity(
                                                tableName = table,
                                                itemId = idStr,
                                                payload = localized
                                            )
                                        )
                                        repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                            level = "INFO",
                                            message = "Sync success: Ingested and secured 1 item into table '$table'."
                                        ))
                                    }
                                } catch (e: Exception) {
                                    repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                        level = "ERROR",
                                        message = "Failed saving HTTP database posts: ${e.message}"
                                    ))
                                }
                            }
                        }

                        responseCode = 200
                        responseText = "{\"success\":true,\"message\":\"Data synchronized and localized with local Dynamic Table '$table'. Hashed sensitive fields, downloaded media.\"}"
                    } else if (method == "GET") {
                        val query = exchange.requestURI.query
                        val queryMap = if (!query.isNullOrBlank()) {
                            query.split("&").associate {
                                val split = it.split("=")
                                val k = split.getOrNull(0) ?: ""
                                val v = split.getOrNull(1) ?: ""
                                k to v
                            }
                        } else emptyMap()

                        val table = queryMap["table"] ?: "users"
                        val itemId = queryMap["id"]

                        responseText = if (itemId != null) {
                            val row = runBlocking { repository.getDynamicRow(table, itemId) }
                            if (row != null) {
                                row.payload
                            } else {
                                responseCode = 404
                                "{\"success\":false,\"error\":\"Item ID $itemId not found in $table table.\"}"
                            }
                        } else {
                            val rows = runBlocking { repository.getDynamicRowsByTableSync(table) }
                            val arr = JSONArray()
                            rows.forEach {
                                arr.put(JSONObject(it.payload))
                            }
                            arr.toString()
                        }
                    } else {
                        responseCode = 405
                        responseText = "Method Not Allowed"
                    }
                } catch (e: Exception) {
                    responseCode = 500
                    responseText = "{\"success\":false,\"error\":\"${e.localizedMessage?.replace("\"", "\\\"")}\"}"
                }

                val bytes = responseText.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(responseCode, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.executor = null
            server.start()
            localHttpServer = server
            Log.i(TAG, "Local stand-alone integration HTTP API Server successfully listening on port 8085")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to boot standalone HTTP server: ${e.message}")
        }
    }

    private fun stopLocalHttpServer() {
        try {
            localHttpServer?.stop(0)
            localHttpServer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Http Server: ${e.message}")
        }
    }

    private fun startWebsitePolling() {
        websitePollJob?.cancel()
        websitePollJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                val isEnabled = authManager.isWebsitePollingEnabled()
                val url = authManager.getWebsiteUrl()
                val intervalSec = authManager.getWebsitePollingIntervalSec().coerceAtLeast(5)

                if (isEnabled && url.isNotBlank() && authManager.isWebsiteConnected()) {
                    Log.d(TAG, "Starting periodic background website synchronization poll... targeting: $url")
                    val tablesToSync = listOf("users", "products")
                    for (table in tablesToSync) {
                        try {
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                .build()

                            // Append table query parameter safely
                            val parsedUri = url.toHttpUrlOrNull()
                            if (parsedUri != null) {
                                val httpUrl = parsedUri.newBuilder()
                                    .addQueryParameter("table", table)
                                    .addQueryParameter("action", "poll")
                                    .build()

                                val request = okhttp3.Request.Builder()
                                    .url(httpUrl)
                                    .addHeader("X-Publishable-Key", authManager.getWebsitePublishableKey())
                                    .addHeader("X-Secret-Token", authManager.getWebsiteSecretToken())
                                    .get()
                                    .build()

                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        var bodyText = response.body?.string() ?: ""
                                        if (bodyText.isNotBlank()) {
                                            try {
                                                val aesKey = authManager.getWebsiteDecryptionKey()
                                                if (bodyText.trim().startsWith("{")) {
                                                    val testObj = JSONObject(bodyText)
                                                    if (testObj.has("encrypted_data")) {
                                                        val encStr = testObj.getString("encrypted_data")
                                                        val decStr = com.example.util.AesEncryptionHelper.decrypt(encStr, aesKey)
                                                        if (decStr != encStr) {
                                                            bodyText = decStr
                                                            repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                                                level = "INFO",
                                                                message = "Decrypt success on background poll table '$table' using symmetric AES key."
                                                            ))
                                                        }
                                                    }
                                                }

                                                if (bodyText.trim().startsWith("[")) {
                                                    val arr = JSONArray(bodyText)
                                                    for (i in 0 until arr.length()) {
                                                        val rowItem = arr.getJSONObject(i)
                                                        val idStr = if (rowItem.has("id")) {
                                                            rowItem.get("id").toString()
                                                        } else {
                                                            UUID.randomUUID().toString()
                                                        }
                                                        
                                                        val hashed = com.example.util.SmsGatewaySecurityAndSyncUtils.hashSensitiveDataInPayload(rowItem.toString())
                                                        val localized = com.example.util.SmsGatewaySecurityAndSyncUtils.downloadAndLocalizeImagesInPayload(applicationContext, hashed, repository)

                                                        repository.insertDynamicRow(
                                                            com.example.data.db.DynamicRowEntity(
                                                                tableName = table,
                                                                itemId = idStr,
                                                                payload = localized
                                                            )
                                                        )
                                                    }
                                                    repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                                        level = "INFO",
                                                        message = "Background poll auto-imported and secured ${arr.length()} items into '$table'."
                                                    ))
                                                } else if (bodyText.trim().startsWith("{")) {
                                                    val obj = JSONObject(bodyText)
                                                    val dataArray = obj.optJSONArray("data")
                                                    if (dataArray != null) {
                                                        for (i in 0 until dataArray.length()) {
                                                            val rowItem = dataArray.getJSONObject(i)
                                                            val idStr = if (rowItem.has("id")) rowItem.get("id").toString() else UUID.randomUUID().toString()
                                                            
                                                            val hashed = com.example.util.SmsGatewaySecurityAndSyncUtils.hashSensitiveDataInPayload(rowItem.toString())
                                                            val localized = com.example.util.SmsGatewaySecurityAndSyncUtils.downloadAndLocalizeImagesInPayload(applicationContext, hashed, repository)

                                                            repository.insertDynamicRow(
                                                                com.example.data.db.DynamicRowEntity(
                                                                    tableName = table,
                                                                    itemId = idStr,
                                                                    payload = localized
                                                                )
                                                            )
                                                        }
                                                        repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                                            level = "INFO",
                                                            message = "Background poll auto-imported and secured ${dataArray.length()} items into '$table' from data envelope."
                                                        ))
                                                    } else {
                                                        val idStr = if (obj.has("id")) obj.get("id").toString() else UUID.randomUUID().toString()
                                                        
                                                        val hashed = com.example.util.SmsGatewaySecurityAndSyncUtils.hashSensitiveDataInPayload(obj.toString())
                                                        val localized = com.example.util.SmsGatewaySecurityAndSyncUtils.downloadAndLocalizeImagesInPayload(applicationContext, hashed, repository)

                                                        repository.insertDynamicRow(
                                                            com.example.data.db.DynamicRowEntity(
                                                                tableName = table,
                                                                itemId = idStr,
                                                                payload = localized
                                                            )
                                                        )
                                                        repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                                            level = "INFO",
                                                            message = "Background poll auto-imported 1 item into '$table'."
                                                        ))
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                                    level = "ERROR",
                                                    message = "Background poll parsing failure for table '$table': ${e.message}"
                                                ))
                                            }
                                        }
                                    } else {
                                        repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                            level = "WARNING",
                                            message = "Background poll for '$table' returned status ${response.code}."
                                        ))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Network failure background website polling table $table: ${e.message}")
                        }
                    }
                }
                delay(intervalSec * 1000L)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        authManager.setGatewayRunning(false)
        
        pollJob?.cancel()
        heartbeatJob?.cancel()
        statusUpdateJob?.cancel()
        scheduledSmsJob?.cancel()
        websitePollJob?.cancel()
        serviceJob.cancel()

        smsSentReceivers.forEach { (jobId, receiver) ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
        smsSentReceivers.clear()

        _serviceState.value = _serviceState.value.copy(
            isRunning = false,
            isConnected = false,
            connectionMessage = "Stopped"
        )

        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        releaseWakeLock()
        stopLocalHttpServer()
        super.onDestroy()
    }

    private fun showLocalNotification(title: String, body: String, isSuccess: Boolean) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val outcomeChannelId = "sms_gateway_outcomes_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var channel = manager.getNotificationChannel(outcomeChannelId)
                if (channel == null) {
                    channel = NotificationChannel(
                        outcomeChannelId,
                        "SMS Gateway Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alerts for sent and failed SMS jobs"
                        enableLights(true)
                        lightColor = if (isSuccess) android.graphics.Color.GREEN else android.graphics.Color.RED
                        enableVibration(true)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
            
            val iconRes = if (isSuccess) {
                android.R.drawable.ic_dialog_info
            } else {
                android.R.drawable.ic_dialog_alert
            }
            
            val builder = NotificationCompat.Builder(this, outcomeChannelId)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
            
            val notificationId = (System.currentTimeMillis() % 100000).toInt() + (if (isSuccess) 100000 else 200000)
            manager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error showing outcome local notification: ${e.message}")
        }
    }
}
