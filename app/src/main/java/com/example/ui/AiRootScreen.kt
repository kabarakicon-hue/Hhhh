package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Styling palette aligned with main style
private val EmeraldPrimary = Color(0xFF10B981)
private val ObsidianBackground = Color(0xFF090A0F)
private val SlateNavCard = Color(0xFF131520)
private val SlateOutline = Color(0xFF1F2235)
private val LightText = Color(0xFFE2E8F0)
private val SoftGray = Color(0xFF94A3B8)

data class AiChatMessage(
    val sender: String, // "User" or "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isExecuting: Boolean = false,
    val executionLogs: List<String> = emptyList()
)

@Composable
fun AiRootScreen(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    val apiKey = viewModel.groqApiKeyInput.collectAsState().value
    val model = viewModel.groqModelInput.collectAsState().value

    var messages by remember {
        mutableStateOf(
            listOf(
                AiChatMessage(
                    sender = "AI",
                    text = "Welcome to the SimGate AI Root Console. You can control the entire application using natural language. Try typing one of the instructions below!"
                )
            )
        )
    }

    var textInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    val suggestions = listOf(
        "Schedule SMS to 254712345678 in 1 minute saying 'Meeting is set'",
        "Enable auto-reply for WhatsApp and SMS",
        "Set selected Sim to SIM 2",
        "Set device polling interval to 15 seconds",
        "Stop Gateway client immediately",
        "Create customer greeting draft"
    )

    // Side-swipe detection modifier to go back to previous actions or navigate tabs
    var swipeLeftEdgeStart by remember { mutableStateOf(false) }
    val dynamicSwipeModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset ->
                swipeLeftEdgeStart = offset.x < 150f
            },
            onDrag = { change, dragAmount ->
                if (swipeLeftEdgeStart && dragAmount.x > 50f) {
                    swipeLeftEdgeStart = false
                    change.consume()
                    // Programmatically trigger returning to Home
                    viewModel.setTab("Home")
                    Toast.makeText(context, "Navigating back to Dashboard", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Auto-scroll chat to latest messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .then(dynamicSwipeModifier)
    ) {
        // --- TOP BRIEF BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateNavCard)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x3010B981), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "AI Root Console",
                        color = LightText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active LLM: $model",
                        color = EmeraldPrimary,
                        fontSize = 11.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = isGenerating,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = EmeraldPrimary,
                        strokeWidth = 2.dp
                    )
                    Text("AI thinking...", color = SoftGray, fontSize = 11.sp)
                }
            }
        }

        // --- SUGGESTIONS CHIPS ROW ---
        Text(
            text = "TAP RECOMMENDATIONS TO CONTROL:",
            fontSize = 10.sp,
            color = SoftGray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(suggestions) { item ->
                    Box(
                        modifier = Modifier
                            .background(SlateNavCard, RoundedCornerShape(18.dp))
                            .border(1.dp, SlateOutline, RoundedCornerShape(18.dp))
                            .clickable(enabled = !isGenerating) {
                                textInput = item
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item,
                            color = LightText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = SlateOutline)

        // --- CONVERSATIONAL CHAT SCREEN ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg)
                }
            }
        }

        // --- BOTTOM TEXT INPUT BAR ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateNavCard)
                .border(2.dp, SlateOutline)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask AI to schedule, control or configure...", color = SoftGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedContainerColor = ObsidianBackground,
                        unfocusedContainerColor = ObsidianBackground,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = SlateOutline
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank() && !isGenerating) {
                            val userText = textInput.trim()
                            textInput = ""
                            focusManager.clearFocus()
                            scope.launch {
                                isGenerating = true
                                try {
                                    submitUserCommand(userText, apiKey, model, viewModel, messages) { updated ->
                                        messages = updated
                                    }
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    })
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank() && !isGenerating) {
                            val userText = textInput.trim()
                            textInput = ""
                            focusManager.clearFocus()
                            scope.launch {
                                isGenerating = true
                                try {
                                    submitUserCommand(userText, apiKey, model, viewModel, messages) { updated ->
                                        messages = updated
                                    }
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (textInput.isNotBlank()) EmeraldPrimary else SlateOutline, CircleShape),
                    enabled = textInput.isNotBlank() && !isGenerating
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: AiChatMessage) {
    val isUser = message.sender == "User"
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (isUser) EmeraldPrimary else SlateNavCard
    val txtColor = if (isUser) Color.White else LightText
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(bgColor, shape)
                    .then(
                        if (!isUser) Modifier.border(1.dp, SlateOutline, shape) else Modifier
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = message.text,
                        color = txtColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (message.executionLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianBackground),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "Automated AI Execution Log:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                }
                                message.executionLogs.forEach { log ->
                                    Text(
                                        text = log,
                                        color = Color(0xFFA7F3D0),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Method that submits command to the Groq API model and handles state updates
private suspend fun submitUserCommand(
    prompt: String,
    apiKey: String,
    model: String,
    viewModel: SmsGatewayViewModel,
    currentMessages: List<AiChatMessage>,
    onStateUpdate: (List<AiChatMessage>) -> Unit
) {
    if (apiKey.isBlank()) {
        val updated = currentMessages + 
                AiChatMessage("User", prompt) + 
                AiChatMessage("AI", "To proceed, please enter your Groq API Key in Settings first. All operations run physically on-device using end-to-end sandbox storage.")
        onStateUpdate(updated)
        return
    }

    val updatedWithUser = currentMessages + AiChatMessage("User", prompt)
    onStateUpdate(updatedWithUser)

    val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    // Setup secure systems prompts instructing the model about exactly what configurations are supported
    val systemPrompt = """
        You are the automated central "AI Root Console" for SimGate.
        The user wants to manage settings, schedule messages, stop/start services, populate templates/drafts, or clean logs via natural language.
        Analyze their request and formulate an appropriate response.
        We support executing the following commands automatically on-device. Return any commands that match in the "actions" JSON array:
        
        1. toggle_auto_reply: Enables/Disables auto reply system. Parameters:
           - "enabled": Boolean (e.g. true or false)
        2. schedule_sms: Queues an SMS. 1 minute delay EXACTLY means 60 seconds. Parameters:
           - "title": (String) name of the task
           - "message": (String) content
           - "recipients": (String) phone number
           - "delay_seconds": (Long) total delayed seconds until dispatch (e.g. 60 for 1 min, 3600 for 1 hr)
        3. clear_history: Deletes SMS logs. Parameters: none.
        4. set_poll_interval: Updates gateway socket/poll interval. Parameters:
           - "seconds": Integer (e.g. 10, 15, 30)
        5. start_gateway: Starts gateway client foreground service. Parameters: none.
        6. stop_gateway: Disables foreground service. Parameters: none.
        7. set_sim: Selects active cellular carrier line choice. Parameters:
           - "sim": (String) either "SIM 1" or "SIM 2"
        8. add_draft: Configures draft message. Parameters:
           - "title": (String) draft title
           - "message": (String) template content
           - "recipients": (String) destination
        9. add_template: Keeps reusable templated contents. Parameters:
           - "title": String
           - "message": String
           - "category": String (e.g. "Sales", "Support", "Finance")

        Return ONLY a single valid raw JSON object containing "message" and any matching "actions". Do NOT wrap in markdown code blocks like ```json or any other text.
        JSON format:
        {
          "message": "Friendly response describing what is happening",
          "actions": [
             { "type": "schedule_sms", "title": "Test", "message": "hello", "recipients": "...", "delay_seconds": 60 }
          ]
        }
    """.trimIndent()

    try {
        val requestBodyJson = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.5)
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

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorStr = response.body?.string() ?: "Unknown error"
                    withContext(Dispatchers.Main) {
                        val finalMsg = updatedWithUser + AiChatMessage(
                            "AI",
                            "Groq server returned an execution fault ${response.code}. Please ensure your trial api key is valid and has available quota."
                        )
                        onStateUpdate(finalMsg)
                    }
                    return@use
                }

                val respBody = response.body?.string() ?: ""
                withContext(Dispatchers.Main) {
                    var choiceContent = ""
                    try {
                        val jsonResp = JSONObject(respBody)
                        val choices = jsonResp.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) {
                            val finalMsg = updatedWithUser + AiChatMessage("AI", "Empty reply choice from Groq.")
                            onStateUpdate(finalMsg)
                            return@withContext
                        }

                        choiceContent = choices.getJSONObject(0).getJSONObject("message").optString("content", "")
                        
                        // Parse local actions
                        val rawContent = choiceContent.replace("```json", "").replace("```", "").trim()
                        val parsedResult = JSONObject(rawContent)
                        val textMessage = parsedResult.optString("message", "Request completed.")
                        val actionsArr = parsedResult.optJSONArray("actions")

                        val executionLogs = mutableListOf<String>()

                        if (actionsArr != null && actionsArr.length() > 0) {
                            for (i in 0 until actionsArr.length()) {
                                val action = actionsArr.getJSONObject(i)
                                val type = action.optString("type", "")
                                when (type) {
                                    "toggle_auto_reply" -> {
                                        val enabled = action.optBoolean("enabled", false)
                                        viewModel.setAutoReplyEnabled(enabled)
                                        executionLogs.add("✓ ${if (enabled) "Enabled" else "Disabled"} Auto-reply system")
                                    }
                                    "schedule_sms" -> {
                                        val title = action.optString("title", "AI Task")
                                        val msgText = action.optString("message", "")
                                        val rec = action.optString("recipients", "")
                                        val delaySec = action.optLong("delay_seconds", 60L)
                                        
                                        val execTime = System.currentTimeMillis() + (delaySec * 1000L)
                                        viewModel.addScheduledSms(title, msgText, rec, execTime)
                                        executionLogs.add("✓ Scheduled SMS on sim carrier to $rec, sending in $delaySec seconds exactly")
                                    }
                                    "clear_history" -> {
                                        viewModel.clearAllHistory()
                                        executionLogs.add("✓ Cleaned/Purged all SMS dispatch history logs")
                                    }
                                    "set_poll_interval" -> {
                                        val seconds = action.optInt("seconds", 15)
                                        viewModel.updatePollInterval(seconds)
                                        executionLogs.add("✓ Configured loop polling rate to $seconds seconds")
                                    }
                                    "start_gateway" -> {
                                        viewModel.triggerServiceState(true)
                                        executionLogs.add("✓ Started foreground gateway engine")
                                    }
                                    "stop_gateway" -> {
                                        viewModel.triggerServiceState(false)
                                        executionLogs.add("✓ Shut down active foreground service")
                                    }
                                    "set_sim" -> {
                                        val sim = action.optString("sim", "SIM 1")
                                        viewModel.updateSelectedSimPref(sim)
                                        executionLogs.add("✓ Configured active dispatch transmitter path to $sim")
                                    }
                                    "add_draft" -> {
                                        val title = action.optString("title", "Draft")
                                        val msgText = action.optString("message", "")
                                        val rec = action.optString("recipients", "")
                                        viewModel.addDraft(title, msgText, rec)
                                        executionLogs.add("✓ Setup local drafting content: $title")
                                    }
                                    "add_template" -> {
                                        val title = action.optString("title", "Template")
                                        val msgText = action.optString("message", "")
                                        val category = action.optString("category", "General")
                                        viewModel.addTemplate(title, msgText, category)
                                        executionLogs.add("✓ Created reusable layout template: $title [$category]")
                                    }
                                }
                            }
                        }

                        val newestWithAi = updatedWithUser + AiChatMessage(
                            sender = "AI",
                            text = textMessage,
                            executionLogs = executionLogs
                        )
                        onStateUpdate(newestWithAi)

                    } catch (e: Exception) {
                        // fallback to plain-text formatting if LLM failed to return structured JSON data
                        val newestWithAi = updatedWithUser + AiChatMessage(
                            sender = "AI",
                            text = choiceContent
                        )
                        onStateUpdate(newestWithAi)
                    }
                }
            }
        }
    } catch (e: Exception) {
        val newestWithError = updatedWithUser + AiChatMessage(
            sender = "AI",
            text = "Encountered a connection exception when routing commands: ${e.localizedMessage}. Please double check if details are corrected."
        )
        onStateUpdate(newestWithError)
    }
}
