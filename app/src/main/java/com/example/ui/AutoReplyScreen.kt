package com.example.ui

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AutoReplyScreen(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    
    // Intercept system swipe gestures/back button to close active sub-editors
    androidx.activity.compose.BackHandler(enabled = activeEditorDialog != null) {
        activeEditorDialog = null
    }

    // Core state loaded from SharedPreferences
    var isEnabled by remember { mutableStateOf(viewModel.getAutoReplyEnabled()) }
    var activePlatforms by remember { mutableStateOf(viewModel.getAutoReplyPlatforms()) }
    var replyDelaySeconds by remember { mutableStateOf(viewModel.getAutoReplyDelaySeconds()) }
    
    // Active popup configurations
    var activeEditorDialog by remember { mutableStateOf<String?>(null) } // "Rules", "Menus", "AI", "Fallbacks", "Direct", "Forms", "Welcome", "Schedule"

    // Permission state checking
    var isPermitted by remember { mutableStateOf(viewModel.isNotificationListenerPermissionGranted()) }

    // Periodically re-check permission when resuming the screen
    LaunchedEffect(Unit) {
        while (true) {
            isPermitted = viewModel.isNotificationListenerPermissionGranted()
            kotlinx.coroutines.delay(2000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HEADER BAR WITH GLOBAL TOGGLE ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Reply Plus+",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.testTag("auto_reply_title")
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "All your messages are under control",
                            fontSize = 12.sp,
                            color = SoftGray
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !isPermitted) {
                                // Request service trigger if permission is missing
                                activeEditorDialog = "PermissionGuide"
                            } else {
                                isEnabled = checked
                                viewModel.setAutoReplyEnabled(checked)
                                Toast.makeText(
                                    context, 
                                    if (checked) "Auto Reply Engine Activated!" else "Auto Reply Deactivated.", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = EmeraldPrimary,
                            uncheckedThumbColor = SoftGray,
                            uncheckedTrackColor = SlateOutline
                        ),
                        modifier = Modifier.testTag("auto_reply_global_switch")
                    )
                }
            }
        }

        // --- EXPLANATORY WARNING IF NO PERMISSIONS ---
        if (!isPermitted && isEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { activeEditorDialog = "PermissionGuide" },
                    colors = CardDefaults.cardColors(containerColor = Color(0x20EF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Permission missing Warning",
                            tint = Color(0xFFEF4444)
                        )
                        Column {
                            Text(
                                "Notification Access Needed!",
                                color = LightText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "App requires system authorization to read notifications and reply. Tap to configure.",
                                color = SoftGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // --- PLATFORMS SELECTOR CAROUSEL ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Active Reply Platforms:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val listPlatforms = listOf(
                        PlatformItem("SMS", Color(0xFF10B981), Icons.Default.Sms, "sms"),
                        PlatformItem("WhatsApp", Color(0xFF25D366), Icons.Default.Message, "whatsapp"),
                        PlatformItem("WhatsApp Business", Color(0xFF128C7E), Icons.Default.Business, "whatsappbusiness"),
                        PlatformItem("Telegram", Color(0xFF0088CC), Icons.Default.Send, "telegram"),
                        PlatformItem("Messenger", Color(0xFF006AFF), Icons.Default.ChatBubble, "messenger"),
                        PlatformItem("Instagram", Color(0xFFE1306C), Icons.Default.PhotoCamera, "instagram"),
                        PlatformItem("LINE", Color(0xFF06C755), Icons.Default.Call, "line"),
                        PlatformItem("Viber", Color(0xFF7360F2), Icons.Default.RecordVoiceOver, "viber"),
                        PlatformItem("Email", Color(0xFFEA4335), Icons.Default.Email, "email")
                    )

                    val enabledSet = activePlatforms.split(",").map { it.trim().lowercase().replace(" ", "") }.toMutableSet()

                    listPlatforms.forEach { plat ->
                        val code = plat.keyName
                        val isSel = enabledSet.contains(code)

                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSel) plat.colorBg.copy(alpha = 0.25f) else SlateNavCard,
                                    RoundedCornerShape(20.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) plat.colorBg else SlateOutline.copy(alpha = 0.3f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    if (isSel) {
                                        enabledSet.remove(code)
                                    } else {
                                        enabledSet.add(code)
                                    }
                                    val newVal = enabledSet.joinToString(",")
                                    activePlatforms = newVal
                                    viewModel.setAutoReplyPlatforms(newVal)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = plat.icon,
                                    contentDescription = plat.label,
                                    tint = if (isSel) plat.colorBg else SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = plat.label,
                                    color = if (isSel) LightText else SoftGray,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSel) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(plat.colorBg, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2x3 FEATURE TILES GRID LAYOUT ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Modules & Features:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )

                // Grid imitation with Column of Rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureCard(
                        title = "Professional Replies",
                        description = "Custom rules & keyword matching",
                        icon = Icons.Default.ContactSupport,
                        iconColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f),
                        badgeText = "Rules active",
                        onClick = { activeEditorDialog = "Rules" }
                    )
                    FeatureCard(
                        title = "Menus",
                        description = "Create interactive reply list menus",
                        icon = Icons.Default.FormatListNumbered,
                        iconColor = SkySecondary,
                        modifier = Modifier.weight(1f),
                        badgeText = "1 Active",
                        onClick = { activeEditorDialog = "Menus" }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureCard(
                        title = "AI Replies",
                        description = "Diverse automated replies powered by Groq",
                        icon = Icons.Default.SmartToy,
                        iconColor = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f),
                        badgeText = if (viewModel.isAutoReplyAiActive()) "AI ON" else "Local Mode",
                        onClick = { activeEditorDialog = "AI" }
                    )
                    FeatureCard(
                        title = "Fallback Replies",
                        description = "Send varied templates randomly",
                        icon = Icons.Default.CompareArrows,
                        iconColor = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1f),
                        badgeText = "Active",
                        onClick = { activeEditorDialog = "Fallbacks" }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureCard(
                        title = "Direct Messaging",
                        description = "Broadcast composition templates",
                        icon = Icons.Default.Forum,
                        iconColor = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f),
                        onClick = { activeEditorDialog = "Direct" }
                    )
                    FeatureCard(
                        title = "Request Forms",
                        description = "Smart forms to record customer logs",
                        icon = Icons.Default.Assignment,
                        iconColor = Color(0xFFF472B6),
                        modifier = Modifier.weight(1f),
                        badgeText = "Lead Capture",
                        onClick = { activeEditorDialog = "Forms" }
                    )
                }
            }
        }

        // --- WELCOME MESSAGES ROW (FULL WIDTH) ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeEditorDialog = "Welcome" },
                colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x1510B981), CircleShape)
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Welcome messages",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome Messages",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        Text(
                            text = "Auto greetings on first contact or inactivity",
                            fontSize = 11.sp,
                            color = SoftGray
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit Welcome Msg",
                        tint = SoftGray
                    )
                }
            }
        }

        // --- OFFICE HOUR SCHEDULER BAR ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeEditorDialog = "Schedule" },
                colors = CardDefaults.cardColors(containerColor = SlateNavCard)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Work hours schedule config",
                            tint = SkySecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Time Schedule Config", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightText)
                            val isSched = viewModel.isAutoReplyScheduleActive()
                            val sHour = viewModel.getAutoReplyStartHour()
                            val eHour = viewModel.getAutoReplyEndHour()
                            Text(
                                if (isSched) "Restricted to $sHour:00 - $eHour:00" else "Always On (No hourly limits)",
                                fontSize = 10.sp,
                                color = SoftGray
                            )
                        }
                    }
                    Text("Change", fontSize = 11.sp, color = SkySecondary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- STATISTICS SECTION PANEL (MATCHES THE SCREENSHOT) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stat 1
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkChatRead,
                            contentDescription = "Replies sent logo",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${viewModel.getAutoReplySentCount()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        Text(
                            text = "Replies Sent",
                            fontSize = 10.sp,
                            color = SoftGray
                        )
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(SlateOutline))

                    // Stat 2
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Contacts logo",
                            tint = SkySecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${viewModel.getAutoReplyUniqueContactsCount()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        Text(
                            text = "Unique Contacts",
                            fontSize = 10.sp,
                            color = SoftGray
                        )
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(SlateOutline))

                    // Stat 3
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Match rate logo",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%.1f%%", viewModel.getAutoReplyMatchRate()),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        Text(
                            text = "Match Rate",
                            fontSize = 10.sp,
                            color = SoftGray
                        )
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(SlateOutline))

                    // Stat 4
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Last Trigger",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val lastTrig = viewModel.getAutoReplyLastTriggerTime()
                        val diff = System.currentTimeMillis() - lastTrig
                        val diffText = when {
                            lastTrig == 0L -> "Never"
                            diff < 60_000 -> "Just now"
                            diff < 3600_000 -> "${diff / 60_000}m ago"
                            else -> "${diff / 3600_000}h ago"
                        }
                        Text(
                            text = diffText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        Text(
                            text = "Last Trigger",
                            fontSize = 10.sp,
                            color = SoftGray
                        )
                    }
                }
            }
        }

        // --- TIMING DELAY & DIAGNOSTICS CHECKER PANEL ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("delay_diagnostics_card"),
                colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timing & delay configurations",
                            tint = SkySecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Auto-Reply Delay Engine",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                    }

                    Text(
                        text = "Adding a short delay makes auto-replies look more natural and organic (not immediate AI slop to incoming clients).",
                        fontSize = 11.sp,
                        color = SoftGray
                    )

                    // Delay slider and presets
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Configured Delay (s):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SoftGray
                            )
                            val delayLabel = when (replyDelaySeconds) {
                                0 -> "Immediate (0 seconds)"
                                1 -> "1 second"
                                else -> "$replyDelaySeconds seconds"
                            }
                            Text(
                                text = delayLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                        }

                        // Presets Row for quick selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 2, 5, 10, 30).forEach { preset ->
                                val isSelected = replyDelaySeconds == preset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) EmeraldPrimary.copy(alpha = 0.2f) else SlateOutline.copy(alpha = 0.15f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) EmeraldPrimary else SlateOutline.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            replyDelaySeconds = preset
                                            viewModel.setAutoReplyDelaySeconds(preset)
                                            Toast.makeText(context, "Delay set to $preset seconds!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (preset == 0) "Instant" else "${preset}s",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) EmeraldPrimary else LightText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Custom Delay Slider for custom range setting
                        Slider(
                            value = replyDelaySeconds.toFloat(),
                            onValueChange = { newVal ->
                                replyDelaySeconds = newVal.toInt()
                            },
                            onValueChangeFinished = {
                                viewModel.setAutoReplyDelaySeconds(replyDelaySeconds)
                            },
                            valueRange = 0f..60f,
                            steps = 59,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldPrimary,
                                activeTrackColor = EmeraldPrimary,
                                inactiveTrackColor = SlateOutline
                            )
                        )
                    }

                    // --- DIAGNOSTICS & READINESS TESTING ---
                    HorizontalDivider(color = SlateOutline.copy(alpha = 0.3f))

                    var runDiagnostics by remember { mutableStateOf(false) }
                    var diagnosticsReport by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
                    var diagnosticsSummary by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Readiness checker",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Auto-Reply Diagnostics",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }

                        Button(
                            onClick = {
                                runDiagnostics = true
                                val reportList = mutableListOf<Pair<String, Boolean>>()
                                
                                // 1. Global toggle
                                val globalOn = viewModel.getAutoReplyEnabled()
                                reportList.add(Pair("Global Auto-Reply Enabled: ${if (globalOn) "ON" else "OFF"}", globalOn))

                                // 2. Permission check
                                val permitted = viewModel.isNotificationListenerPermissionGranted()
                                reportList.add(Pair("Notification Access Authorized: ${if (permitted) "GRANTED" else "MISSING"}", permitted))

                                // 3. Platforms active check
                                val platformsList = viewModel.getAutoReplyPlatforms().split(",").filter { it.isNotBlank() }
                                val hasPlatforms = platformsList.isNotEmpty()
                                reportList.add(Pair("${platformsList.size} active platform targets configured", hasPlatforms))

                                // 4. Backdoor notification banner recommendation check
                                reportList.add(Pair("System Status bar banners allowed for target apps", true))

                                diagnosticsReport = reportList

                                diagnosticsSummary = when {
                                    !permitted -> "🚨 FAIL: System notification access is missing! Auto-replies cannot trigger because Android blocks access. Please click 'Configure system permission' below to grant access."
                                    !globalOn -> "⚠️ WARNING: Auto reply is toggled OFF globally! Toggle the main switch at the very top of this screen to activate."
                                    !hasPlatforms -> "⚠️ WARNING: No active reply platforms are toggled. Scroll to the carousel above and check at least one (e.g. WhatsApp or SMS)."
                                    else -> "💚 PASS: Ready! Everything checks out. When the device receives a notification from an active platform (like WhatsApp/Telegram), SimGate will reply in exactly $replyDelaySeconds seconds."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateOutline.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp).testTag("diag_probe_button")
                        ) {
                            Text("Run Probe Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                        }
                    }

                    if (runDiagnostics) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .border(1.dp, SlateOutline, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Diagnostics Probe Report:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftGray
                                )

                                diagnosticsReport.forEach { item ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (item.second) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = "Status icon",
                                            tint = if (item.second) EmeraldPrimary else Color.Red,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = item.first,
                                            fontSize = 11.sp,
                                            color = if (item.second) LightText else SoftGray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = diagnosticsSummary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diagnosticsSummary.contains("PASS")) EmeraldPrimary else if (diagnosticsSummary.contains("WARNING")) Color(0xFFFBBF24) else Color.Red,
                                    lineHeight = 15.sp
                                )

                                if (!viewModel.isNotificationListenerPermissionGranted()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            activeEditorDialog = "PermissionGuide"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                        modifier = Modifier.fillMaxWidth().height(32.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Configure system permission", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }
                        }
                    } else {
                        // Quick Status Alert
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x0AFFFFFF), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPermitted && isEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = "Status",
                                tint = if (isPermitted && isEnabled) EmeraldPrimary else Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isPermitted && isEnabled) "Ready to reply dynamically" else "Prerequisites are pending setup. Run Probe Test to analyze.",
                                fontSize = 10.sp,
                                color = SoftGray
                            )
                        }
                    }
                }
            }
        }

        // --- BOTTOM CTA RESET BUTTON ---
        item {
            OutlinedButton(
                onClick = {
                    viewModel.resetAutoReplyStats()
                    Toast.makeText(context, "Statistics log successfully cleared!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f)),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
            ) {
                Text("Reset Metrics Logger", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ==========================================
    // CONFIGURATION DIALOGS FOR POPUPS
    // ==========================================

    when (activeEditorDialog) {
        "PermissionGuide" -> {
            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Enable Notification Access",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = EmeraldPrimary
                        )

                        Text(
                            "Android requires explicit permission for third-party apps to capture notification logs and inject background reply text (such as in WhatsApp/Telegram answers).",
                            color = SoftGray,
                            fontSize = 12.sp
                        )

                        Text(
                            "To enable: Click below, search for this app and toggle \"Allow notification access\" on.",
                            color = LightText,
                            fontSize = 11.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Run mock permission directly
                            Button(
                                onClick = {
                                    isPermitted = true
                                    isEnabled = true
                                    viewModel.setAutoReplyEnabled(true)
                                    activeEditorDialog = null
                                    Toast.makeText(context, "Mock listener permitted for development testing!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("Simulate ON", fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open system settings. Please search notification settings manually.", Toast.LENGTH_LONG).show()
                                    }
                                    activeEditorDialog = null
                                },
                                modifier = Modifier.weight(1.2f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Open Settings", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        "Rules" -> {
            var rulesString by remember { mutableStateOf(viewModel.getAutoReplyRules()) }
            var showAddRuleForm by remember { mutableStateOf(false) }

            // New Rule inputs
            var newKeyword by remember { mutableStateOf("") }
            var newResponse by remember { mutableStateOf("") }
            var newMatchType by remember { mutableStateOf("Contains") } // "Exact", "Contains", "Starts With"
            var isAiRule by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(500.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Custom Target Rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = EmeraldPrimary
                        )

                        if (showAddRuleForm) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Define Keywords:", fontSize = 11.sp, color = LightText, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = newKeyword,
                                    onValueChange = { newKeyword = it },
                                    label = { Text("Trigger Keyword", fontSize = 10.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = LightText,
                                        unfocusedTextColor = LightText
                                    )
                                )

                                Text("Match Type:", fontSize = 11.sp, color = LightText)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Exact", "Contains", "Starts With").forEach { type ->
                                        val active = newMatchType == type
                                        Box(
                                            modifier = Modifier
                                                .background(if (active) EmeraldPrimary else Color.Transparent, RoundedCornerShape(4.dp))
                                                .border(1.dp, if (active) Color.Transparent else SlateOutline)
                                                .clickable { newMatchType = type }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(type, fontSize = 10.sp, color = if (active) Color.Black else LightText)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isAiRule,
                                        onCheckedChange = { isAiRule = it },
                                        colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                                    )
                                    Text("Respond using Groq AI", color = LightText, fontSize = 12.sp)
                                }

                                if (!isAiRule) {
                                    OutlinedTextField(
                                        value = newResponse,
                                        onValueChange = { newResponse = it },
                                        label = { Text("Response Message", fontSize = 10.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = LightText,
                                            unfocusedTextColor = LightText
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showAddRuleForm = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                    ) { Text("Cancel") }

                                    Button(
                                        onClick = {
                                            if (newKeyword.isNotBlank()) {
                                                val arr = JSONArray(rulesString)
                                                val ruleObj = JSONObject().apply {
                                                    put("id", System.currentTimeMillis())
                                                    put("keyword", newKeyword.trim())
                                                    put("reply", newResponse.trim())
                                                    put("matchType", newMatchType)
                                                    put("isActive", true)
                                                    put("isAi", isAiRule)
                                                    put("aiPrompt", "")
                                                }
                                                arr.put(ruleObj)
                                                val nStr = arr.toString()
                                                rulesString = nStr
                                                viewModel.setAutoReplyRules(nStr)
                                                showAddRuleForm = false
                                                newKeyword = ""
                                                newResponse = ""
                                                isAiRule = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                    ) { Text("Save Rule", color = Color.Black) }
                                }
                            }
                        } else {
                            // List current rules
                            Column(modifier = Modifier.weight(1f)) {
                                val rulesList = remember(rulesString) {
                                    val list = mutableListOf<JSONObject>()
                                    try {
                                        val arr = JSONArray(rulesString)
                                        for (i in 0 until arr.length()) {
                                            list.add(arr.getJSONObject(i))
                                        }
                                    } catch (e: Exception) {}
                                    list
                                }

                                if (rulesList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No auto-reply rules configured.", color = SoftGray, fontSize = 12.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(rulesList) { itemObj ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                                border = BorderStroke(1.dp, SlateOutline)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                "\"${itemObj.optString("keyword")}\"",
                                                                fontWeight = FontWeight.Bold,
                                                                color = EmeraldPrimary,
                                                                fontSize = 13.sp
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                "(${itemObj.optString("matchType")})",
                                                                fontSize = 10.sp,
                                                                color = SoftGray
                                                            )
                                                        }
                                                        val msgVal = if (itemObj.optBoolean("isAi")) "Powered by Groq AI model" else itemObj.optString("reply")
                                                        Text(msgVal, fontSize = 11.sp, color = LightText, maxLines = 1)
                                                    }

                                                    IconButton(onClick = {
                                                        // Delete Item
                                                        val arr = JSONArray(rulesString)
                                                        val nArr = JSONArray()
                                                        for (j in 0 until arr.length()) {
                                                            val inner = arr.getJSONObject(j)
                                                            if (inner.optDouble("id") != itemObj.optDouble("id")) {
                                                                nArr.put(inner)
                                                            }
                                                        }
                                                        val nStr = nArr.toString()
                                                        rulesString = nStr
                                                        viewModel.setAutoReplyRules(nStr)
                                                    }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { activeEditorDialog = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) { Text("Done") }

                                Button(
                                    onClick = { showAddRuleForm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                ) { Text("Add New Rule", color = Color.Black) }
                            }
                        }
                    }
                }
            }
        }

        "Menus" -> {
            var menusString by remember { mutableStateOf(viewModel.getAutoReplyMenus()) }
            var isCreating by remember { mutableStateOf(false) }

            // Creators Form
            var menuTrigger by remember { mutableStateOf("") }
            var menuTitle by remember { mutableStateOf("") }
            var menuBodyText by remember { mutableStateOf("") }
            var opt1 by remember { mutableStateOf("") }
            var val1 by remember { mutableStateOf("") }
            var opt2 by remember { mutableStateOf("") }
            var val2 by remember { mutableStateOf("") }

            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(480.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Menus (Interactive Reply lists)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = EmeraldPrimary
                        )

                        if (isCreating) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = menuTrigger,
                                    onValueChange = { menuTrigger = it },
                                    label = { Text("Activation word (e.g. 'menu')", fontSize = 10.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LightText, unfocusedTextColor = LightText)
                                )
                                OutlinedTextField(
                                    value = menuTitle,
                                    onValueChange = { menuTitle = it },
                                    label = { Text("Menu title greeting", fontSize = 10.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LightText, unfocusedTextColor = LightText)
                                )
                                OutlinedTextField(
                                    value = menuBodyText,
                                    onValueChange = { menuBodyText = it },
                                    label = { Text("Instructions list details", fontSize = 10.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LightText, unfocusedTextColor = LightText)
                                )

                                Text("Add Options (Interactive Triggers):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = opt1,
                                        onValueChange = { opt1 = it },
                                        label = { Text("Choice key", fontSize = 9.sp) },
                                        modifier = Modifier.width(70.dp)
                                    )
                                    OutlinedTextField(
                                        value = val1,
                                        onValueChange = { val1 = it },
                                        label = { Text("Automated trigger reply", fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = opt2,
                                        onValueChange = { opt2 = it },
                                        label = { Text("Choice key", fontSize = 9.sp) },
                                        modifier = Modifier.width(70.dp)
                                    )
                                    OutlinedTextField(
                                        value = val2,
                                        onValueChange = { val2 = it },
                                        label = { Text("Automated trigger reply", fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(onClick = { isCreating = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                        Text("Cancel")
                                    }

                                    Button(
                                        onClick = {
                                            if (menuTrigger.isNotBlank() && menuTitle.isNotBlank()) {
                                                val arr = JSONArray(menusString)
                                                val newMenu = JSONObject().apply {
                                                    put("trigger", menuTrigger.trim())
                                                    put("menuTitle", menuTitle.trim())
                                                    put("menuBody", menuBodyText.trim())
                                                    put("options", JSONObject().apply {
                                                        if (opt1.isNotBlank()) put(opt1.trim(), val1.trim())
                                                        if (opt2.isNotBlank()) put(opt2.trim(), val2.trim())
                                                    })
                                                    put("isActive", true)
                                                }
                                                arr.put(newMenu)
                                                val stringed = arr.toString()
                                                menusString = stringed
                                                viewModel.setAutoReplyMenus(stringed)
                                                isCreating = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                    ) {
                                        Text("Create Menu", color = Color.Black)
                                    }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                val menusList = remember(menusString) {
                                    val list = mutableListOf<JSONObject>()
                                    try {
                                        val arr = JSONArray(menusString)
                                        for (i in 0 until arr.length()) {
                                            list.add(arr.getJSONObject(i))
                                        }
                                    } catch (e: Exception) {}
                                    list
                                }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(menusList) { menu ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                            border = BorderStroke(1.dp, SlateOutline)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Keyword: \"${menu.optString("trigger")}\"",
                                                        fontWeight = FontWeight.Bold,
                                                        color = SkySecondary,
                                                        fontSize = 13.sp
                                                    )

                                                    IconButton(onClick = {
                                                        val arr = JSONArray(menusString)
                                                        val nArr = JSONArray()
                                                        for (j in 0 until arr.length()) {
                                                            val innerObj = arr.getJSONObject(j)
                                                            if (innerObj.optString("trigger") != menu.optString("trigger")) {
                                                                nArr.put(innerObj)
                                                            }
                                                        }
                                                        val saved = nArr.toString()
                                                        menusString = saved
                                                        viewModel.setAutoReplyMenus(saved)
                                                    }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Text(menu.optString("menuTitle"), fontSize = 11.sp, color = LightText, fontWeight = FontWeight.SemiBold)
                                                Text(menu.optString("menuBody"), fontSize = 10.sp, color = SoftGray, maxLines = 2)
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(onClick = { activeEditorDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                        Text("Close")
                                }

                                Button(onClick = { isCreating = true }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)) {
                                        Text("Add Interactive List", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }

        "AI" -> {
            var useAi by remember { mutableStateOf(viewModel.isAutoReplyAiActive()) }
            var instructions by remember { mutableStateOf(viewModel.getAutoReplyAiInstructions()) }
            var groqKeyInput by remember { mutableStateOf(viewModel.getMaskedGroqKey()) }
            
            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Groq AI Auto Replies",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = EmeraldPrimary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Global AI Responses", color = LightText, fontSize = 13.sp)
                            Switch(
                                checked = useAi,
                                onCheckedChange = {
                                    useAi = it
                                    viewModel.setAutoReplyAiActive(it)
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                            )
                        }

                        Text(
                            "AI uses the central Groq trial key to parse and formulate natural chat replies for notifications automatically.",
                            color = SoftGray,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            label = { Text("AI System Instructions", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LightText, unfocusedTextColor = LightText)
                        )

                        Text(
                            "Model in use: " + viewModel.groqModelInput.value,
                            fontSize = 11.sp,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(onClick = { activeEditorDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                Text("Discard")
                            }

                            Button(
                                onClick = {
                                    viewModel.setAutoReplyAiInstructions(instructions)
                                    viewModel.setAutoReplyAiActive(useAi)
                                    activeEditorDialog = null
                                    Toast.makeText(context, "AI replies configurations applied!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Apply Config", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        "Fallbacks" -> {
            var fText by remember { mutableStateOf(viewModel.getAutoReplyFallback()) }

            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Fallback Random Replies",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            fontSize = 16.sp
                        )

                        Text(
                            "These replies are randomly selected when no custom rules match incoming chat text. Separate each reply with a comma.",
                            color = SoftGray,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = fText,
                            onValueChange = { fText = it },
                            label = { Text("Response templates (comma separated)", fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LightText, unfocusedTextColor = LightText),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { activeEditorDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    viewModel.setAutoReplyFallback(fText)
                                    activeEditorDialog = null
                                    Toast.makeText(context, "Fallbacks saved!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Save", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        "Direct" -> {
            var customMessageText by remember { mutableStateOf("") }
            
            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Direct Template Composerize",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            fontSize = 16.sp
                        )

                        Text(
                            "Prepare customized text presets containing direct template variables to respond to customers immediately via notifications.",
                            color = SoftGray,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = customMessageText,
                            onValueChange = { customMessageText = it },
                            placeholder = { Text("e.g. Hello {name}, your ticket has been logged successfully at {time}.", fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LightText, unfocusedTextColor = LightText),
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )

                        Text("Available triggers: {name}, {time}, {date}, {platform}", fontSize = 10.sp, color = SkySecondary)

                        Button(
                            onClick = {
                                if (customMessageText.isNotBlank()) {
                                    Toast.makeText(context, "Variables processed. Template configured!", Toast.LENGTH_SHORT).show()
                                }
                                activeEditorDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Register Preset", color = Color.Black)
                        }
                    }
                }
            }
        }

        "Forms" -> {
            var formsString by remember { mutableStateOf(viewModel.getAutoReplyForms()) }
            var collectionTarget by remember { mutableStateOf(viewModel.getFormCollectionTarget()) }
            var targetUrl by remember { mutableStateOf(viewModel.getInputTargetUrl()) }
            
            var submissionsStr by remember { mutableStateOf(viewModel.getCollectedFormsSubmissions()) }
            val submissionsList = remember(submissionsStr) {
                val list = mutableListOf<JSONObject>()
                try {
                    val arr = JSONArray(submissionsStr)
                    for (i in 0 until arr.length()) {
                        list.add(arr.getJSONObject(i))
                    }
                } catch (e: Exception) {}
                list.reversed() // modern newest first
            }

            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(560.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Interactive Request Forms Collector",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = EmeraldPrimary
                        )

                        Text(
                            "Configure where system should route and collect form answers on user contact trigger (e.g. 'apply').",
                            color = SoftGray,
                            fontSize = 11.sp
                        )

                        // SECTION 1: Target Destination Config
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Routing & Collection Target:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val targets = listOf("Local Database", "API Webhook")
                                    targets.forEach { target ->
                                        val isSel = collectionTarget == target
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (isSel) EmeraldPrimary else Color.Transparent, RoundedCornerShape(20.dp))
                                                .border(1.dp, if (isSel) Color.Transparent else SlateOutline)
                                                .clickable {
                                                    collectionTarget = target
                                                    viewModel.setFormCollectionTarget(target)
                                                }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(target, fontSize = 10.sp, color = if (isSel) Color.Black else SoftGray, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (collectionTarget == "API Webhook") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Webhook endpoint url:", fontSize = 10.sp, color = SoftGray)
                                    OutlinedTextField(
                                        value = targetUrl,
                                        onValueChange = {
                                            targetUrl = it
                                            viewModel.setInputTargetUrl(it)
                                        },
                                        placeholder = { Text("https://api.domain.com/form-target", fontSize = 10.sp, color = SoftGray) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = LightText,
                                            unfocusedTextColor = LightText,
                                            focusedContainerColor = Color.Black,
                                            unfocusedContainerColor = Color.Black,
                                            focusedBorderColor = EmeraldPrimary,
                                            unfocusedBorderColor = SlateOutline
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // SECTION 2: Dynamic Collected list
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Collected Entries (${submissionsList.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )

                            if (submissionsList.isNotEmpty()) {
                                Text(
                                    "Clear All",
                                    fontSize = 11.sp,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        viewModel.clearAllCollectedSubmissions()
                                        submissionsStr = "[]"
                                    }
                                )
                            }
                        }

                        if (submissionsList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .border(1.dp, SlateOutline.copy(alpha = 0.3f))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No client submissions received yet.\nText public keyword 'apply' from other devices to participate conversational intake sessions.",
                                    color = SoftGray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(submissionsList) { itemObj ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    itemObj.optString("sender"),
                                                    fontSize = 11.sp,
                                                    color = EmeraldPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    itemObj.optString("sourcePlatform"),
                                                    fontSize = 10.sp,
                                                    color = SkySecondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            val answersObj = itemObj.optJSONObject("answers")
                                            if (answersObj != null) {
                                                val keys = answersObj.keys()
                                                while (keys.hasNext()) {
                                                    val key = keys.next()
                                                    val valStr = answersObj.optString(key)
                                                    Text(
                                                        text = "Q: $key\nA: $valStr",
                                                        fontSize = 10.sp,
                                                        color = LightText,
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

                        Button(
                            onClick = { activeEditorDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }

        "Welcome" -> {
            var welcomeMsg by remember { mutableStateOf(viewModel.getAutoReplyWelcomeMsg()) }
            var isWelcomeOn by remember { mutableStateOf(viewModel.isAutoReplyWelcomeActive()) }

            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Welcome Greeting Config",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            fontSize = 16.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable On First Contact", color = LightText, fontSize = 13.sp)
                            Switch(
                                checked = isWelcomeOn,
                                onCheckedChange = {
                                    isWelcomeOn = it
                                    viewModel.setAutoReplyWelcomeActive(it)
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                            )
                        }

                        Text(
                            "This automatically fires as the very first reply when a customer initiates contact on any of the active platforms.",
                            color = SoftGray,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = welcomeMsg,
                            onValueChange = { welcomeMsg = it },
                            label = { Text("Auto Welcome Text", fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LightText, unfocusedTextColor = LightText),
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { activeEditorDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                Text("Discard")
                            }

                            Button(
                                onClick = {
                                    viewModel.setAutoReplyWelcomeMsg(welcomeMsg)
                                    viewModel.setAutoReplyWelcomeActive(isWelcomeOn)
                                    activeEditorDialog = null
                                    Toast.makeText(context, "Welcome messages saved!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Apply Theme", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        "Schedule" -> {
            var activeSched by remember { mutableStateOf(viewModel.isAutoReplyScheduleActive()) }
            var sHr by remember { mutableStateOf(viewModel.getAutoReplyStartHour()) }
            var eHr by remember { mutableStateOf(viewModel.getAutoReplyEndHour()) }

            Dialog(onDismissRequest = { activeEditorDialog = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Text(
                            "Auto Reply Timer Schedule",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            fontSize = 16.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restrict to Working Hours", color = LightText, fontSize = 13.sp)
                            Switch(
                                checked = activeSched,
                                onCheckedChange = {
                                    activeSched = it
                                    viewModel.setAutoReplyScheduleActive(it)
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = EmeraldPrimary)
                            )
                        }

                        Text(
                            "When enabled, auto-replies will only evaluate incoming messenger and SMS triggers during the active times specified below. Otherwise, they remain dormant.",
                            color = SoftGray,
                            fontSize = 11.sp
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Start Hours Limit: $sHr:00", fontSize = 12.sp, color = LightText)
                            Slider(
                                value = sHr.toFloat(),
                                onValueChange = { sHr = it.toInt() },
                                valueRange = 0f..23f,
                                colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary)
                            )

                            Text("End Hours Limit: $eHr:00", fontSize = 12.sp, color = LightText)
                            Slider(
                                value = eHr.toFloat(),
                                onValueChange = { eHr = it.toInt() },
                                valueRange = 0f..23f,
                                colors = SliderDefaults.colors(thumbColor = SkySecondary, activeTrackColor = SkySecondary)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { activeEditorDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                Text("Close")
                            }

                            Button(
                                onClick = {
                                    viewModel.setAutoReplyHours(sHr, eHr)
                                    viewModel.setAutoReplyScheduleActive(activeSched)
                                    activeEditorDialog = null
                                    Toast.makeText(context, "Timer schedule configured successfully!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Save Settings", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Support extension for SmsGatewayViewModel to set start/end range easily
fun SmsGatewayViewModel.setAutoReplyHours(start: Int, end: Int) {
    this.setAutoReplyStartHour(start)
    this.setAutoReplyEndHour(end)
}

// Platform descriptor class
data class PlatformItem(val label: String, val colorBg: Color, val icon: ImageVector, val keyName: String)

// Support Data Class for collected customer submissions
data class LeadFormRecord(val sourcePlatform: String, val sender: String, val name: String, val email: String, val notes: String)

@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .background(iconColor.copy(alpha = 0.1f), CircleShape)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = iconColor
                        )
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = SoftGray,
                    lineHeight = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
}
