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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        if (!authManager.hasCredentials()) {
            Log.e(TAG, "Cannot start service: No credentials saved!")
            stopSelf()
            return
        }

        authManager.setGatewayRunning(true)
        _serviceState.value = _serviceState.value.copy(
            isRunning = true,
            connectionMessage = "Reconnecting..."
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
        val notification = buildServiceNotification("Connecting to server...")
        startForeground(NOTIFICATION_ID, notification)

        // Perform initial connect call
        connectToServer()
    }

    private fun stopGatewayService() {
        Log.d(TAG, "Stopping service")
        authManager.setGatewayRunning(false)

        pollJob?.cancel()
        heartbeatJob?.cancel()
        statusUpdateJob?.cancel()
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
    }

    private fun connectToServer() {
        val devId = authManager.getDeviceId() ?: return
        val devToken = authManager.getDeviceToken() ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to sever for validation...")
                val response = RetrofitClient.getService(this@GatewayService).connectDevice(ConnectRequest(devId, devToken))
                if (response.isSuccessful) {
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
                RetrofitClient.getService(this@GatewayService).reportSent(ReportSentRequest(devId, devToken, job.jobId))

            } catch (e: Exception) {
                Log.e(TAG, "Error reporting SMS success: ${e.message}")
            }
        }
    }

    private fun reportFailure(job: SmsJob, reason: String, simDetails: String) {
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
                RetrofitClient.getService(this@GatewayService).reportFailed(ReportFailedRequest(devId, devToken, job.jobId, reason))

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

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        authManager.setGatewayRunning(false)
        
        pollJob?.cancel()
        heartbeatJob?.cancel()
        statusUpdateJob?.cancel()
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
        super.onDestroy()
    }
}
