package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthManager(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "sms_gateway_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("sms_gateway_prefs_clear", Context.MODE_PRIVATE)
    }

    fun saveCredentials(deviceId: String, deviceToken: String, apiUrl: String = "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/") {
        sharedPreferences.edit()
            .putString("device_id", deviceId)
            .putString("device_token", deviceToken)
            .putString("api_url", apiUrl)
            .apply()
    }

    fun getDeviceId(): String? {
        return sharedPreferences.getString("device_id", null)
    }

    fun getDeviceToken(): String? {
        return sharedPreferences.getString("device_token", null)
    }

    fun getApiUrl(): String {
        return sharedPreferences.getString("api_url", "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/") ?: "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/"
    }

    fun clearCredentials() {
        sharedPreferences.edit()
            .remove("device_id")
            .remove("device_token")
            .remove("api_url")
            .apply()
    }

    fun setGatewayRunning(running: Boolean) {
        sharedPreferences.edit().putBoolean("gateway_running", running).apply()
    }

    fun isGatewayRunning(): Boolean {
        return sharedPreferences.getBoolean("gateway_running", false)
    }

    fun getSelectedSim(): String {
        return sharedPreferences.getString("selected_sim", "Auto") ?: "Auto"
    }

    fun setSelectedSim(sim: String) {
        sharedPreferences.edit().putString("selected_sim", sim).apply()
    }

    fun hasCredentials(): Boolean {
        return !getDeviceId().isNullOrBlank() && !getDeviceToken().isNullOrBlank()
    }
}
