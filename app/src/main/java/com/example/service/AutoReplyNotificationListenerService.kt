package com.example.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.pref.AuthManager
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class AutoReplyNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    private lateinit var authManager: AuthManager

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager(applicationContext)
        Log.d("AutoReplyService", "AutoReplyNotificationListenerService Created!")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // 1. Check if Auto Reply is enabled globally
        if (!authManager.isAutoReplyEnabled()) {
            return
        }

        // 2. Schedule Check: if restricted to specific hours
        if (authManager.isAutoReplyScheduleActive()) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val startHour = authManager.getAutoReplyStartHour()
            val endHour = authManager.getAutoReplyEndHour()
            if (startHour <= endHour) {
                if (currentHour < startHour || currentHour > endHour) return
            } else { // Overnight schedule (e.g. 22:00 to 06:00)
                if (currentHour in (endHour + 1)..<startHour) return
            }
        }

        val packageName = sbn.packageName ?: return

        // 3. Map package name to Platform
        val platform = mapPackageToPlatform(packageName) ?: return

        // 4. Check if this platform is toggled on
        val enabledPlatforms = authManager.getAutoReplyPlatforms()
            .split(",")
            .map { it.trim().lowercase() }
        
        val targetPlatformKey = platform.lowercase().replace(" ", "")
        if (!enabledPlatforms.contains(targetPlatformKey)) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val senderTitle = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (senderTitle.isBlank() || messageText.isBlank()) return

        // Ignore double replies or messages sent by the user themselves
        val selfName = "you"
        if (senderTitle.lowercase() == selfName) return

        // 5. Look for wear reply action (Direct reply option in notification)
        val actionPair = findWearableReplyAction(sbn.notification) ?: return

        serviceScope.launch {
            try {
                val delaySeconds = authManager.getAutoReplyDelaySeconds()
                if (delaySeconds > 0) {
                    Log.d("AutoReplyService", "Delaying auto-reply by $delaySeconds seconds...")
                    delay(delaySeconds * 1000L)
                }
                processAndReply(
                    sender = senderTitle,
                    message = messageText,
                    platform = platform,
                    action = actionPair.first,
                    remoteInput = actionPair.second
                )
            } catch (e: Exception) {
                Log.e("AutoReplyService", "Error processing notification reply: ${e.message}", e)
            }
        }
    }

    private fun mapPackageToPlatform(pkg: String): String? {
        return when {
            pkg.contains("com.whatsapp.w4b") -> "WhatsApp Business"
            pkg.contains("com.whatsapp") -> "WhatsApp"
            pkg.contains("org.telegram.messenger") || pkg.contains("org.thunderdog.challegram") -> "Telegram"
            pkg.contains("com.facebook.orca") -> "Messenger"
            pkg.contains("com.instagram.android") -> "Instagram"
            pkg.contains("jp.naver.line.android") -> "LINE"
            pkg.contains("com.viber.voip") -> "Viber"
            pkg.contains("com.google.android.apps.messaging") || pkg.contains("android.telephony") -> "SMS"
            pkg.contains("com.google.android.gm") || pkg.contains("com.microsoft.office.outlook") || pkg.contains("mail") -> "Email"
            else -> null
        }
    }

    private fun findWearableReplyAction(notification: Notification): Pair<Notification.Action, RemoteInput>? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey != null && remoteInput.allowFreeFormInput) {
                    return Pair(action, remoteInput)
                }
            }
        }
        return null
    }

    private suspend fun processAndReply(
        sender: String,
        message: String,
        platform: String,
        action: Notification.Action,
        remoteInput: RemoteInput
    ) {
        val msgClean = message.trim()
        val senderClean = sender.trim()

        var matchedRule = false
        var replyText = ""

        // 1. Check Menus (Interactive templates list) first
        val menusJson = authManager.getAutoReplyMenus()
        if (menusJson.isNotBlank()) {
            try {
                val menusArray = JSONArray(menusJson)
                for (i in 0 until menusArray.length()) {
                    val menuObj = menusArray.getJSONObject(i)
                    if (menuObj.optBoolean("isActive", true)) {
                        val trigger = menuObj.optString("trigger").trim()
                        
                        // User requested menu or trigger keyword
                        if (msgClean.equals(trigger, ignoreCase = true)) {
                            replyText = menuObj.optString("menuTitle") + "\n" + menuObj.optString("menuBody")
                            matchedRule = true
                            break
                        }

                        // Sender responded with an option
                        val optionsObj = menuObj.optJSONObject("options")
                        if (optionsObj != null && optionsObj.has(msgClean)) {
                            replyText = optionsObj.optString(msgClean)
                            matchedRule = true
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoReplyService", "Menu processing error: ${e.message}")
            }
        }

        // 1.5 Check if an active multi-step conversational form session is in progress for this sender
        val activeSessionStr = authManager.getFormSession(senderClean)
        if (activeSessionStr.isNotBlank() && !matchedRule) {
            try {
                val sessionObj = JSONObject(activeSessionStr)
                val formTrigger = sessionObj.optString("formTrigger")
                val currentIndex = sessionObj.optInt("currentIndex", 0)
                val answers = sessionObj.optJSONObject("answers") ?: JSONObject()

                // Find the form being filled
                val formsJson = authManager.getAutoReplyForms()
                var activeFormObj: JSONObject? = null
                if (formsJson.isNotBlank()) {
                    val formsArray = JSONArray(formsJson)
                    for (i in 0 until formsArray.length()) {
                        val form = formsArray.getJSONObject(i)
                        if (form.optString("trigger").equals(formTrigger, ignoreCase = true)) {
                            activeFormObj = form
                            break
                        }
                    }
                }

                if (activeFormObj != null) {
                    val fieldsList = activeFormObj.optJSONArray("fields")
                    if (fieldsList != null) {
                        // Save the answer we just received
                        if (currentIndex < fieldsList.length()) {
                            val lastQuestion = fieldsList.optString(currentIndex)
                            answers.put(lastQuestion, message)
                        }

                        val nextIndex = currentIndex + 1
                        if (nextIndex < fieldsList.length()) {
                            // Update session and send next question
                            sessionObj.put("currentIndex", nextIndex)
                            sessionObj.put("answers", answers)
                            authManager.setFormSession(senderClean, sessionObj.toString())

                            replyText = "Question ${nextIndex + 1}/${fieldsList.length()}: " + fieldsList.getString(nextIndex)
                            matchedRule = true
                        } else {
                            // Form completed! Save it as a submission
                            val submissionObj = JSONObject().apply {
                                put("id", System.currentTimeMillis())
                                put("sourcePlatform", platform)
                                put("sender", senderClean)
                                put("timestamp", System.currentTimeMillis())
                                put("formTitle", activeFormObj.optString("formTitle"))
                                put("answers", answers)
                            }
                            
                            authManager.addCollectedFormsSubmission(submissionObj.toString())
                            
                            val collectionMode = authManager.getFormCollectionTarget()
                            if (collectionMode == "API Webhook" || collectionMode == "Sheets Redirect") {
                                val url = authManager.getInputTargetUrl()
                                if (url.startsWith("http")) {
                                    serviceScope.launch {
                                        try {
                                            val body = RequestBody.create(
                                                "application/json; charset=utf-8".toMediaTypeOrNull(),
                                                submissionObj.toString()
                                            )
                                            val req = Request.Builder().url(url).post(body).build()
                                            client.newCall(req).execute().close()
                                        } catch (ex: Exception) {
                                            Log.e("AutoReplyService", "Failed to forward webhook lead: ${ex.message}")
                                        }
                                    }
                                }
                            }

                            authManager.clearFormSession(senderClean)
                            replyText = "Form Completed! Thank you for submitting the ${activeFormObj.optString("formTitle")}. We have collected your responses securely."
                            matchedRule = true
                        }
                    }
                } else {
                    authManager.clearFormSession(senderClean)
                }
            } catch (e: Exception) {
                authManager.clearFormSession(senderClean)
                Log.e("AutoReplyService", "Error in multi-step form progress handling: ${e.message}", e)
            }
        }

        // 2. Check Forms (Interactive customer request collection)
        if (!matchedRule) {
            val formsJson = authManager.getAutoReplyForms()
            if (formsJson.isNotBlank()) {
                try {
                    val formsArray = JSONArray(formsJson)
                    for (i in 0 until formsArray.length()) {
                        val formObj = formsArray.getJSONObject(i)
                        if (formObj.optBoolean("isActive", true)) {
                            val trigger = formObj.optString("trigger").trim()
                            if (msgClean.equals(trigger, ignoreCase = true)) {
                                val fieldsList = formObj.optJSONArray("fields")
                                if (fieldsList != null && fieldsList.length() > 0) {
                                    val sessionObj = JSONObject().apply {
                                        put("formTrigger", trigger)
                                        put("currentIndex", 0)
                                        put("answers", JSONObject())
                                    }
                                    authManager.setFormSession(senderClean, sessionObj.toString())

                                    val firstAsk = fieldsList.getString(0)
                                    replyText = "📋 *${formObj.optString("formTitle")}*\nWe have initiated your request details session.\n\nQuestion 1/${fieldsList.length()}: $firstAsk"
                                    matchedRule = true
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AutoReplyService", "Forms parsing error: ${e.message}")
                }
            }
        }

        // 3. Check Professional Custom Rules
        if (!matchedRule) {
            val rulesJson = authManager.getAutoReplyRules()
            if (rulesJson.isNotBlank()) {
                try {
                    val rulesArray = JSONArray(rulesJson)
                    for (i in 0 until rulesArray.length()) {
                        val ruleObj = rulesArray.getJSONObject(i)
                        if (ruleObj.optBoolean("isActive", true)) {
                            val kw = ruleObj.optString("keyword").trim().lowercase()
                            val mType = ruleObj.optString("matchType", "Contains")
                            
                            val isMatch = when (mType) {
                                "Exact" -> msgClean.lowercase() == kw
                                "Starts With" -> msgClean.lowercase().startsWith(kw)
                                else -> msgClean.lowercase().contains(kw) // Contains
                            }

                            if (isMatch) {
                                matchedRule = true
                                val isAi = ruleObj.optBoolean("isAi", false)
                                val aiEnabled = authManager.isAutoReplyAiActive()
                                if ((isAi || aiEnabled) && authManager.getGroqApiKey().isNotBlank()) {
                                    val aiPrompt = ruleObj.optString("aiPrompt").ifBlank { authManager.getAutoReplyAiInstructions() }
                                    replyText = callGroqReply(message, aiPrompt)
                                } else {
                                    replyText = ruleObj.optString("reply")
                                }
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AutoReplyService", "Rules parse error: ${e.message}")
                }
            }
        }

        // 4. Default to Welcome Messages or Fallback Replies
        if (!matchedRule) {
            val contacts = authManager.getAutoReplyUniqueContacts().split(",").map { it.trim() }.filter { it.isNotBlank() }
            val isNewSender = !contacts.contains(senderClean)

            if (isNewSender && authManager.isAutoReplyWelcomeActive()) {
                replyText = authManager.getAutoReplyWelcomeMessage()
                matchedRule = true
            } else {
                val fallbacks = authManager.getAutoReplyFallbackReplies().split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (fallbacks.isNotEmpty()) {
                    replyText = fallbacks.random()
                    matchedRule = true
                }
            }
        }

        if (replyText.isNotBlank()) {
            sendDirectReply(applicationContext, action, remoteInput, replyText)
            authManager.incrementAutoReplyStats(senderClean, matched = matchedRule)
        }
    }

    private fun sendDirectReply(context: Context, action: Notification.Action, remoteInput: RemoteInput, replyText: String) {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(remoteInput.resultKey, replyText)
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
        try {
            action.actionIntent.send(context, 0, intent)
            Log.d("AutoReplyService", "Successfully injected notification reply automatically!")
        } catch (e: Exception) {
            Log.e("AutoReplyService", "Failed to send notification action: ${e.message}")
        }
    }

    private suspend fun callGroqReply(userMsg: String, systemPrompt: String): String {
        val apiKey = authManager.getGroqApiKey()
        val model = authManager.getGroqModel()
        if (apiKey.isBlank()) return "AI reply requested but Groq API key is missing. Set it in settings!"
        
        return try {
            val requestBodyJson = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "User message: \"$userMsg\". Reply naturally.")
                    })
                })
                put("temperature", 0.7)
            }

            val body = RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                requestBodyJson.toString()
            )

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errDetails = response.body?.string() ?: ""
                    Log.e("AutoReplyService", "Groq error: ${response.code}; Details: $errDetails")
                    return "Error generating reply (Groq error: ${response.code})"
                }
                val respBody = response.body?.string() ?: ""
                val jsonResp = JSONObject(respBody)
                val choices = jsonResp.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).getJSONObject("message").optString("content", "")
                } else {
                    "Thanks for your message!"
                }
            }
        } catch (e: Exception) {
            "AI Reply: Processing error: ${e.message}"
        }
    }
}
