package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

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

    // --- GATEWAY TIMINGS SETTINGS ---
    fun getPollIntervalSec(): Int {
        return sharedPreferences.getInt("poll_interval", 5)
    }

    fun setPollIntervalSec(value: Int) {
        sharedPreferences.edit().putInt("poll_interval", value).apply()
    }

    fun getHeartbeatIntervalSec(): Int {
        return sharedPreferences.getInt("heartbeat_interval", 60)
    }

    fun setHeartbeatIntervalSec(value: Int) {
        sharedPreferences.edit().putInt("heartbeat_interval", value).apply()
    }

    fun isAutoReconnectEnabled(): Boolean {
        return sharedPreferences.getBoolean("auto_reconnect", true)
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_reconnect", enabled).apply()
    }

    fun isStartOnBootEnabled(): Boolean {
        return sharedPreferences.getBoolean("start_on_boot", true)
    }

    fun setStartOnBootEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("start_on_boot", enabled).apply()
    }

    fun isGatewayPaused(): Boolean {
        return sharedPreferences.getBoolean("pause_gateway", false)
    }

    fun setGatewayPaused(paused: Boolean) {
        sharedPreferences.edit().putBoolean("pause_gateway", paused).apply()
    }

    // --- NOTIFICATION SETTINGS ---
    fun isPersistentNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_persistent", true)
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_persistent", enabled).apply()
    }

    fun isSmsSentNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_sms_sent", true)
    }

    fun setSmsSentNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_sms_sent", enabled).apply()
    }

    fun isSmsFailedNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_sms_failed", true)
    }

    fun setSmsFailedNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_sms_failed", enabled).apply()
    }

    fun isIncomingSmsNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_incoming_sms", false)
    }

    fun setIncomingSmsNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_incoming_sms", enabled).apply()
    }

    // --- ONBOARDING & LEGAL SETTINGS ---
    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean("onboarding_completed", completed).apply()
    }

    fun isSchedulePromoShown(): Boolean {
        return sharedPreferences.getBoolean("schedule_promo_shown", false)
    }

    fun setSchedulePromoShown(shown: Boolean) {
        sharedPreferences.edit().putBoolean("schedule_promo_shown", shown).apply()
    }

    fun areTermsAccepted(): Boolean {
        return sharedPreferences.getBoolean("terms_accepted", false)
    }

    fun setTermsAccepted(accepted: Boolean) {
        sharedPreferences.edit().putBoolean("terms_accepted", accepted).apply()
    }

    fun isPrivacyAccepted(): Boolean {
        return sharedPreferences.getBoolean("privacy_accepted", false)
    }

    fun setPrivacyAccepted(accepted: Boolean) {
        sharedPreferences.edit().putBoolean("privacy_accepted", accepted).apply()
    }

    // --- GROQ TRIAL API CONFIGS ---
    fun getGroqApiKey(): String {
        return sharedPreferences.getString("groq_api_key", "") ?: ""
    }

    fun setGroqApiKey(key: String) {
        sharedPreferences.edit().putString("groq_api_key", key).apply()
    }

    fun getGroqModel(): String {
        return sharedPreferences.getString("groq_model", "llama-3.1-8b-instant") ?: "llama-3.1-8b-instant"
    }

    fun setGroqModel(model: String) {
        sharedPreferences.edit().putString("groq_model", model).apply()
    }

    // --- AUTO REPLY MODULE FEATURES ---
    fun isAutoReplyEnabled(): Boolean {
        return sharedPreferences.getBoolean("auto_reply_enabled", false)
    }

    fun setAutoReplyEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_reply_enabled", enabled).apply()
    }

    fun getAutoReplyPlatforms(): String {
        return sharedPreferences.getString("auto_reply_platforms", "SMS,WhatsApp,WhatsAppBusiness,Telegram,Messenger") ?: "SMS,WhatsApp,WhatsAppBusiness,Telegram,Messenger"
    }

    fun setAutoReplyPlatforms(platforms: String) {
        sharedPreferences.edit().putString("auto_reply_platforms", platforms).apply()
    }

    fun getAutoReplyRules(): String {
        val defaultRules = """
            [
              {"id":1,"keyword":"hello","reply":"Hi there! Thanks for reaching out. How can we help you today?","matchType":"Contains","isActive":true,"isAi":false,"aiPrompt":""},
              {"id":2,"keyword":"price","reply":"Our pricing plans are Premium at $19/mo and Enterprise custom. Check out our main dashboard for details!","matchType":"Contains","isActive":true,"isAi":false,"aiPrompt":""},
              {"id":3,"keyword":"support","reply":"Please send your query to support@example.com, and our technical team will resolve your issue.","matchType":"Exact","isActive":true,"isAi":false,"aiPrompt":""}
            ]
        """.trimIndent()
        return sharedPreferences.getString("auto_reply_rules", defaultRules) ?: defaultRules
    }

    fun setAutoReplyRules(rulesJson: String) {
        sharedPreferences.edit().putString("auto_reply_rules", rulesJson).apply()
    }

    fun getAutoReplyMenus(): String {
        val defaultMenus = """
            [
              {
                "trigger": "menu",
                "menuTitle": "Interactive Main Menu",
                "menuBody": "Reply with a number corresponding to your interest:\n1. Open Hours\n2. Office Location\n3. Talk to Agent",
                "options": {
                  "1": "We are open 24/7! Feel free to reach out anytime.",
                  "2": "Our physical office is located at 123 Innovation Drive, Silicon Valley.",
                  "3": "An agent has been notified and will text you shortly at this number."
                },
                "isActive": true
              }
            ]
        """.trimIndent()
        return sharedPreferences.getString("auto_reply_menus", defaultMenus) ?: defaultMenus
    }

    fun setAutoReplyMenus(menusJson: String) {
        sharedPreferences.edit().putString("auto_reply_menus", menusJson).apply()
    }

    fun getAutoReplyForms(): String {
        val defaultForms = """
            [
              {
                "trigger": "apply",
                "formTitle": "Customer Intake Form",
                "fields": ["Can we get your full name?", "What is your email address?", "Describe your business requirements briefly."],
                "isActive": true
              }
            ]
        """.trimIndent()
        return sharedPreferences.getString("auto_reply_forms", defaultForms) ?: defaultForms
    }

    fun setAutoReplyForms(formsJson: String) {
        sharedPreferences.edit().putString("auto_reply_forms", formsJson).apply()
    }

    // --- FORM CONVERSATION SESSION MANAGERS ---
    fun getFormSession(sender: String): String {
        return sharedPreferences.getString("form_session_" + sender.replace("[^a-zA-Z0-9]".toRegex(), ""), "") ?: ""
    }

    fun setFormSession(sender: String, sessionJson: String) {
        sharedPreferences.edit().putString("form_session_" + sender.replace("[^a-zA-Z0-9]".toRegex(), ""), sessionJson).apply()
    }

    fun clearFormSession(sender: String) {
        sharedPreferences.edit().remove("form_session_" + sender.replace("[^a-zA-Z0-9]".toRegex(), "")).apply()
    }

    // --- FORM SUBMISSIONS COLLECTION & COLLECTION TARGETS ---
    fun getFormCollectionTarget(): String {
        return sharedPreferences.getString("form_collection_target", "Local Database") ?: "Local Database"
    }

    fun setFormCollectionTarget(target: String) {
        sharedPreferences.edit().putString("form_collection_target", target).apply()
    }

    fun getInputTargetUrl(): String {
        return sharedPreferences.getString("form_input_target_url", "https://api.example.com/leads") ?: "https://api.example.com/leads"
    }

    fun setInputTargetUrl(url: String) {
        sharedPreferences.edit().putString("form_input_target_url", url).apply()
    }

    fun getCollectedFormsSubmissions(): String {
        val defaultSubmissions = "[]"
        return sharedPreferences.getString("collected_forms_submissions", defaultSubmissions) ?: defaultSubmissions
    }

    fun addCollectedFormsSubmission(submissionJson: String) {
        val current = getCollectedFormsSubmissions()
        try {
            val arr = JSONArray(current)
            arr.put(JSONObject(submissionJson))
            sharedPreferences.edit().putString("collected_forms_submissions", arr.toString()).apply()
        } catch (e: Exception) {
            val arr = JSONArray()
            try {
                arr.put(JSONObject(submissionJson))
            } catch (ex: Exception) {}
            sharedPreferences.edit().putString("collected_forms_submissions", arr.toString()).apply()
        }
    }

    fun clearAllCollectedSubmissions() {
        sharedPreferences.edit().putString("collected_forms_submissions", "[]").apply()
    }

    fun getAutoReplyFallbackReplies(): String {
        val defaultFallbacks = "I am currently away. Your message has been safely received, and our support agents are reviewing it.,Thanks for your message! This is an automated notification confirming we've got you covered.,Got busy here! I'll read this and get back to you as soon as I can."
        return sharedPreferences.getString("auto_reply_fallbacks", defaultFallbacks) ?: defaultFallbacks
    }

    fun setAutoReplyFallbackReplies(fallbacks: String) {
        sharedPreferences.edit().putString("auto_reply_fallbacks", fallbacks).apply()
    }

    fun getAutoReplyWelcomeMessage(): String {
        return sharedPreferences.getString("auto_reply_welcome_msg", "Welcome! Thanks for messaging us. We utilize AI SMS Gateways to speed up replies!") ?: "Welcome! Thanks for messaging us. We utilize AI SMS Gateways to speed up replies!"
    }

    fun setAutoReplyWelcomeMessage(msg: String) {
        sharedPreferences.edit().putString("auto_reply_welcome_msg", msg).apply()
    }

    fun isAutoReplyWelcomeActive(): Boolean {
        return sharedPreferences.getBoolean("auto_reply_welcome_active", true)
    }

    fun setAutoReplyWelcomeActive(active: Boolean) {
        sharedPreferences.edit().putBoolean("auto_reply_welcome_active", active).apply()
    }

    fun isAutoReplyAiActive(): Boolean {
        return sharedPreferences.getBoolean("auto_reply_ai_active", false)
    }

    fun setAutoReplyAiActive(active: Boolean) {
        sharedPreferences.edit().putBoolean("auto_reply_ai_active", active).apply()
    }

    fun getAutoReplyAiInstructions(): String {
        return sharedPreferences.getString("auto_reply_ai_instruct", "You are an AI assistant representing the SMS Gateway platform. Be extremely friendly, precise, and reply in under 120 characters.") ?: "You are an AI assistant representing the SMS Gateway platform. Be extremely friendly, precise, and reply in under 120 characters."
    }

    fun setAutoReplyAiInstructions(instructions: String) {
        sharedPreferences.edit().putString("auto_reply_ai_instruct", instructions).apply()
    }

    fun getAutoReplyStartHour(): Int {
        return sharedPreferences.getInt("auto_reply_start_hour", 8)
    }

    fun setAutoReplyStartHour(hour: Int) {
        sharedPreferences.edit().putInt("auto_reply_start_hour", hour).apply()
    }

    fun getAutoReplyEndHour(): Int {
        return sharedPreferences.getInt("auto_reply_end_hour", 22)
    }

    fun setAutoReplyEndHour(hour: Int) {
        sharedPreferences.edit().putInt("auto_reply_end_hour", hour).apply()
    }

    fun isAutoReplyScheduleActive(): Boolean {
        return sharedPreferences.getBoolean("auto_reply_schedule_active", false)
    }

    fun setAutoReplyScheduleActive(active: Boolean) {
        sharedPreferences.edit().putBoolean("auto_reply_schedule_active", active).apply()
    }

    // --- AUTO REPLY TIME DELAY ---
    fun getAutoReplyDelaySeconds(): Int {
        return sharedPreferences.getInt("auto_reply_delay_seconds", 0)
    }

    fun setAutoReplyDelaySeconds(seconds: Int) {
        sharedPreferences.edit().putInt("auto_reply_delay_seconds", seconds).apply()
    }

    // --- AUTO REPLY STATS ENGINE ---
    fun getAutoReplySentCount(): Int {
        return sharedPreferences.getInt("auto_reply_sent_count", 0)
    }

    fun setAutoReplySentCount(count: Int) {
        sharedPreferences.edit().putInt("auto_reply_sent_count", count).apply()
    }

    fun getAutoReplyUniqueContacts(): String {
        return sharedPreferences.getString("auto_reply_unique_contacts", "") ?: ""
    }

    fun setAutoReplyUniqueContacts(contacts: String) {
        sharedPreferences.edit().putString("auto_reply_unique_contacts", contacts).apply()
    }

    fun getAutoReplyLastTriggerTime(): Long {
        return sharedPreferences.getLong("auto_reply_last_trigger_time", 0L)
    }

    fun setAutoReplyLastTriggerTime(time: Long) {
        sharedPreferences.edit().putLong("auto_reply_last_trigger_time", time).apply()
    }

    fun getAutoReplyMatchRate(): Float {
        return sharedPreferences.getFloat("auto_reply_match_rate", 0.0f)
    }

    fun setAutoReplyMatchRate(rate: Float) {
        sharedPreferences.edit().putFloat("auto_reply_match_rate", rate).apply()
    }

    fun incrementAutoReplyStats(sender: String, matched: Boolean) {
        val totalSent = getAutoReplySentCount() + 1
        setAutoReplySentCount(totalSent)
        setAutoReplyLastTriggerTime(System.currentTimeMillis())

        val contacts = getAutoReplyUniqueContacts().split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val isNew = !contacts.contains(sender.trim())
        if (isNew) {
            contacts.add(sender.trim())
            setAutoReplyUniqueContacts(contacts.joinToString(","))
        }

        // Recalculate generic matched rate
        val oldRate = getAutoReplyMatchRate()
        val currentMatchRatio = if (matched) 1.0f else 0.0f
        val newRate = (oldRate * 0.9f) + (currentMatchRatio * 10f)
        setAutoReplyMatchRate(newRate.coerceIn(50.0f, 100.0f))
    }

    // --- SECURITY PROTOCOL CONFIGS ---
    fun isAntiScreenshotEnabled(): Boolean {
        return sharedPreferences.getBoolean("sec_anti_screenshot", false)
    }

    fun setAntiScreenshotEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("sec_anti_screenshot", enabled).apply()
    }

    fun isSelfDefenseModeEnabled(): Boolean {
        return sharedPreferences.getBoolean("sec_self_defense", false)
    }

    fun setSelfDefenseModeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("sec_self_defense", enabled).apply()
    }

    // --- WEBSITE DATABASE SYNC & TRUST CORE ---
    fun getWebsiteUrl(): String {
        return sharedPreferences.getString("website_url", "") ?: ""
    }

    fun setWebsiteUrl(url: String) {
        sharedPreferences.edit().putString("website_url", url).apply()
    }

    fun isWebsiteConnected(): Boolean {
        return sharedPreferences.getBoolean("website_connected", false)
    }

    fun setWebsiteConnected(connected: Boolean) {
        sharedPreferences.edit().putBoolean("website_connected", connected).apply()
    }

    fun getWebsitePublishableKey(): String {
        var key = sharedPreferences.getString("website_publishable_key", "")
        if (key.isNullOrBlank()) {
            key = generateRandomKey("pk_live_")
            sharedPreferences.edit().putString("website_publishable_key", key).apply()
        }
        return key
    }

    fun getWebsiteSecretToken(): String {
        var token = sharedPreferences.getString("website_secret_token", "")
        if (token.isNullOrBlank()) {
            token = generateRandomKey("sk_live_")
            sharedPreferences.edit().putString("website_secret_token", token).apply()
        }
        return token
    }

    fun regenerateWebsiteKeys() {
        val pk = generateRandomKey("pk_live_")
        val sk = generateRandomKey("sk_live_")
        sharedPreferences.edit()
            .putString("website_publishable_key", pk)
            .putString("website_secret_token", sk)
            .apply()
    }

    fun isWebsitePollingEnabled(): Boolean {
        return sharedPreferences.getBoolean("website_polling_enabled", false)
    }

    fun setWebsitePollingEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("website_polling_enabled", enabled).apply()
    }

    fun getWebsitePollingIntervalSec(): Int {
        return sharedPreferences.getInt("website_polling_interval_sec", 30)
    }

    fun setWebsitePollingIntervalSec(seconds: Int) {
        sharedPreferences.edit().putInt("website_polling_interval_sec", seconds).apply()
    }

    fun getWebsiteDecryptionKey(): String {
        var key = sharedPreferences.getString("website_decryption_key", "")
        if (key.isNullOrBlank()) {
            key = generateRandomAesKey()
            sharedPreferences.edit().putString("website_decryption_key", key).apply()
        }
        return key
    }

    fun regenerateWebsiteDecryptionKey() {
        val key = generateRandomAesKey()
        sharedPreferences.edit().putString("website_decryption_key", key).apply()
    }

    private fun generateRandomAesKey(): String {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..16).map { allowedChars.random() }.joinToString("")
    }

    private fun generateRandomKey(prefix: String): String {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val randomString = (1..32)
            .map { allowedChars.random() }
            .joinToString("")
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(randomString.toByteArray())
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            return "$prefix${hexString.substring(0, 24)}"
        } catch (e: Exception) {
            return "$prefix${randomString.substring(0, 24).lowercase()}"
        }
    }
}
