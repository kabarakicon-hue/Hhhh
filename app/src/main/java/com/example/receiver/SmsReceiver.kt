package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import com.example.data.api.IncomingSmsRequest
import com.example.data.api.RetrofitClient
import com.example.data.db.SmsDatabase
import com.example.data.db.SmsEntity
import com.example.data.db.SmsRepository
import com.example.data.pref.AuthManager
import com.example.service.GatewayService
import com.example.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        Log.d(TAG, "Incoming SMS detected!")
        val authManager = AuthManager(context)
        
        // If the gateway aren't running or configured, do nothing
        if (!authManager.isGatewayRunning() || !authManager.hasCredentials()) {
            Log.d(TAG, "Gateway not active, ignoring incoming SMS.")
            return
        }

        val pendingResult = goAsync()
        val db = SmsDatabase.getDatabase(context)
        val repository = SmsRepository(db.smsDao())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Parse the SMS from the intent
                val messages: Array<SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (!messages.isNullOrEmpty()) {
                    // Combine multi-part messages
                    val sender = messages[0].displayOriginatingAddress ?: "Unknown"
                    val messageBody = messages.joinToString("") { it.displayMessageBody ?: "" }
                    
                    // Determine which SIM received it
                    var simDetails = "Unknown SIM"
                    val subId = intent.getIntExtra("subscription", -1)
                    if (subId != -1) {
                        try {
                            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                            val info = subManager.getActiveSubscriptionInfo(subId)
                            if (info != null) {
                                simDetails = "SIM ${info.simSlotIndex + 1} (${info.carrierName})"
                            }
                        } catch (e: Exception) {
                            simDetails = "Sub ID: $subId"
                        }
                    } else {
                        // Fallback slot index
                        val slot = intent.getIntExtra("slot", -1)
                        if (slot != -1) {
                            simDetails = "SIM ${slot + 1}"
                        }
                    }

                    Log.d(TAG, "SMS parsed successfully: From: $sender, Msg: $messageBody on $simDetails")

                    // 1. Store in local Room database
                    repository.insertLog(
                        SmsEntity(
                            type = "Incoming",
                            phoneNumber = sender,
                            message = messageBody,
                            status = "Received",
                            simUsed = simDetails
                        )
                    )
                    
                    // Update stats in the Gateway service state flow
                    GatewayService.updateStats(context)

                    // 2. Forward to the server API
                    val devId = authManager.getDeviceId()
                    val devToken = authManager.getDeviceToken()

                    if (devId != null && devToken != null) {
                        Log.d(TAG, "Forwarding incoming SMS to backend...")
                        if (!NetworkUtils.isInternetAvailable(context)) {
                            Log.w(TAG, "Skipping incoming SMS forward to backend: Offline (no active internet).")
                        } else {
                            val response = RetrofitClient.getService(context).forwardIncoming(
                                IncomingSmsRequest(
                                    deviceId = devId,
                                    deviceToken = devToken,
                                    sender = sender,
                                    message = messageBody
                                )
                            )
                            if (response.isSuccessful) {
                                Log.d(TAG, "Incoming SMS forwarded successfully!")
                            } else {
                                Log.w(TAG, "Failed forwarding SMS. API error code: ${response.code()}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS receiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
