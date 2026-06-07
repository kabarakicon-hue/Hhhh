package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility for proactive network and internet validation to avoid Supabase URL edge errors
 * when Wifi/cellular is connected but there's no actual internet capability.
 */
object NetworkUtils {

    /**
     * Checks if there's any active network interface (WiFi, Cellular, Ethernet etc.)
     */
    fun isNetworkInterfaceAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }

    /**
     * Checks for actual internet capability by opening a raw socket to safe servers 
     * (e.g., Cloudflare DNS 1.1.1.1 or Google DNS 8.8.8.8) on port 53 with low timeout.
     * This avoids blockages where WiFi/cellular is enabled but no data is actually passing.
     */
    suspend fun isInternetAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkInterfaceAvailable(context)) {
            return@withContext false
        }
        try {
            val timeoutMs = 1500
            val sock1 = Socket()
            val sock2 = Socket()
            
            val cloudflareTask = kotlin.runCatching {
                sock1.connect(InetSocketAddress("1.1.1.1", 53), timeoutMs)
                sock1.close()
                true
            }.getOrDefault(false)

            if (cloudflareTask) return@withContext true

            val googleTask = kotlin.runCatching {
                sock2.connect(InetSocketAddress("8.8.8.8", 53), timeoutMs)
                sock2.close()
                true
            }.getOrDefault(false)

            return@withContext googleTask
        } catch (e: Exception) {
            false
        }
    }
}
