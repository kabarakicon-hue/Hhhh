package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.pref.AuthManager
import com.example.service.GatewayService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device rebooted. Checking if Gateway Service needs to start...")
            val authManager = AuthManager(context)
            if (authManager.isGatewayRunning() && authManager.hasCredentials()) {
                Log.d(TAG, "Gateway was previously running. Starting Gateway Service automatically...")
                val startIntent = Intent(context, GatewayService::class.java).apply {
                    action = GatewayService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(context, startIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start Gateway Service on boot: ${e.message}")
                }
            } else {
                Log.d(TAG, "Gateway was not running or has no credentials. Doing nothing.")
            }
        }
    }
}
