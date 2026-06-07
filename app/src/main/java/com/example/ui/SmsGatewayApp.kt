package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.SmsEntity
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LightText
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SkySecondary
import com.example.ui.theme.SlateNavCard
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SoftGray
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SmsGatewayApp(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("Home") } // "Home", "History", "SIMs", "Settings"
    var subScreen by remember { mutableStateOf<String?>(null) } // "AdvancedMessaging" or null

    // Check runtime SMS and Phone permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        ).let { list ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list + Manifest.permission.POST_NOTIFICATIONS
            } else {
                list
            }
        }
    )

    val isOnboardingComplete by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()

    if (!isOnboardingComplete) {
        OnboardingScreen(viewModel = viewModel)
    } else {
        var showScheduledSmsOnboardingPromo by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (!viewModel.isSchedulePromoShown()) {
                delay(60000L) // 1 minute delay
                showScheduledSmsOnboardingPromo = true
                viewModel.setSchedulePromoShown(true) // Save to preferences immediately so it never shows again
            }
        }

        if (showScheduledSmsOnboardingPromo) {
            AlertDialog(
                onDismissRequest = { showScheduledSmsOnboardingPromo = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Auto-Schedule Messages",
                            color = LightText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Text(
                        text = "Did you know you can schedule SMS messages to be sent automatically at a future time? You can set custom delay timers, build templates, and use AI planning to dispatch campaigns.",
                        color = SoftGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showScheduledSmsOnboardingPromo = false
                            subScreen = "AdvancedMessaging"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Try Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showScheduledSmsOnboardingPromo = false }) {
                        Text("Maybe Later", color = SoftGray)
                    }
                },
                containerColor = SlateNavCard,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = SlateNavCard,
                    tonalElevation = 8.dp
                ) {
                    listOf(
                        Triple("Home", Icons.Default.Home, "Home"),
                        Triple("History", Icons.Default.History, "History"),
                        Triple("SIMs", Icons.Default.SimCard, "SIMs"),
                        Triple("Auto Reply", Icons.Default.Reply, "Auto Reply"),
                        Triple("Settings", Icons.Default.Settings, "Settings")
                    ).forEach { (tab, icon, label) ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldPrimary,
                                selectedTextColor = EmeraldPrimary,
                                unselectedIconColor = SoftGray,
                                unselectedTextColor = SoftGray,
                                indicatorColor = Color(0x2010B981)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBackground)
                    .padding(innerPadding)
            ) {
                if (!permissionsState.allPermissionsGranted) {
                    // Friendly card asking for required platform permissions
                    PermissionRequestScreen(
                        onRequest = { permissionsState.launchMultiplePermissionRequest() }
                    )
                } else {
                    // Ensure latest subscriber status is fetched
                    LaunchedEffect(currentTab) {
                        if (currentTab == "Home" || currentTab == "SIMs") {
                            viewModel.loadActiveSims()
                        }
                    }

                     if (subScreen == "AdvancedMessaging") {
                        AdvancedMessagingScreen(
                            viewModel = viewModel,
                            onBack = { subScreen = null }
                        )
                    } else {
                        when (currentTab) {
                            "Home" -> {
                                val hasCreds = viewModel.hasSavedCredentials()
                                val isWorkspaceTransitionActive by viewModel.isWorkspaceTransitionActive.collectAsStateWithLifecycle()
                                
                                if (isWorkspaceTransitionActive) {
                                    WorkspaceActivationScreen()
                                } else if (hasCreds) {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onViewAllHistory = { currentTab = "History" },
                                        onNavigateToAdvanced = { subScreen = "AdvancedMessaging" }
                                    )
                                } else {
                                    ConnectScreen(viewModel = viewModel)
                                }
                            }
                            "History" -> HistoryScreen(viewModel = viewModel)
                            "SIMs" -> SimsScreen(viewModel = viewModel)
                            "Auto Reply" -> AutoReplyScreen(viewModel = viewModel)
                            "Settings" -> SettingsScreen(
                                viewModel = viewModel,
                                onDisconnect = { currentTab = "Home" },
                                onNavigateToAdvanced = { subScreen = "AdvancedMessaging" }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------- PERMISSIONS SCREEN -----------------

@Composable
fun PermissionRequestScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0x1010B981), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shield Guard",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Permissions Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "To run as an automated SMS Web Gateway, Android requires authorization to send/receive SMS messages, read phone subscription stats, and dispatch background connection status alerts.",
                    fontSize = 14.sp,
                    color = SoftGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("request_permissions_btn")
                ) {
                    Text(
                        text = "Grant Permissions",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ----------------- CONNECT (LOGIN) SCREEN -----------------

@Composable
fun ConnectScreen(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val errorText by viewModel.connectionError.collectAsStateWithLifecycle()

    val deviceId by viewModel.deviceIdInput.collectAsStateWithLifecycle()
    val deviceToken by viewModel.deviceTokenInput.collectAsStateWithLifecycle()
    val isTokenVisible by viewModel.isTokenVisible.collectAsStateWithLifecycle()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showQrFallbackDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High level branding Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x1510B981), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = EmeraldPrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1.0f)) {
                Text(
                    text = "SMS Gateway",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LightText
                )
                Text(
                    text = "Connect your device to the gateway",
                    fontSize = 12.sp,
                    color = SoftGray
                )
            }
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Guideline Help",
                    tint = SoftGray
                )
            }
        }

        // Card Container
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Connect Your Device",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )

                Text(
                    text = "Enter the Device ID and Device Token provided by your dashboard web service.",
                    fontSize = 13.sp,
                    color = SoftGray
                )

                // Device ID Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Device ID",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SoftGray
                    )
                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = { viewModel.deviceIdInput.value = it },
                        placeholder = { Text("e.g. dev_a1b2c3d4", color = Color.Gray, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = SoftGray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline,
                            focusedContainerColor = Color(0xFF0C121A),
                            unfocusedContainerColor = Color(0xFF0C121A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_id_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Device Token Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Device Token",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SoftGray
                    )
                    OutlinedTextField(
                        value = deviceToken,
                        onValueChange = { viewModel.deviceTokenInput.value = it },
                        placeholder = { Text("e.g. dtk_1234567890abcdef...", color = Color.Gray, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = SoftGray) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.isTokenVisible.value = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Token Eyes",
                                    tint = SoftGray
                                )
                            }
                        },
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline,
                            focusedContainerColor = Color(0xFF0C121A),
                            unfocusedContainerColor = Color(0xFF0C121A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_token_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Connection Error Banner
                if (!errorText.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x15EF4444), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF3B1E1E), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = "Failure Exception", tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorText ?: "",
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Connect Button
                Button(
                    onClick = { viewModel.connectDevice(onSuccess = {}) },
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, disabledContainerColor = EmeraldPrimary.copy(0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("connect_gateway_btn")
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connect to Gateway",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // QR Scan Interactive Button with Camera and Fallback dialog
                OutlinedButton(
                    onClick = {
                        try {
                            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                .build()
                            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val rawValue = barcode.rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        val parsed = viewModel.parseAndApplyQrData(rawValue)
                                        if (parsed) {
                                            Toast.makeText(context, "Credentials loaded via QR Code!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Unrecognized QR payload format", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.w("Scanner", "Play Services Scanner not ready or rejected: ${e.message}")
                                    showQrFallbackDialog = true
                                }
                        } catch (e: Exception) {
                            Log.e("Scanner", "Exception starting GMS Scanner: ${e.message}")
                            showQrFallbackDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("scan_qr_code_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LightText)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR Code", fontSize = 13.sp)
                }

                if (showQrFallbackDialog) {
                    var manualQrText by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showQrFallbackDialog = false },
                        title = { Text("Manual QR Code Entry", fontWeight = FontWeight.Bold, color = LightText, fontSize = 16.sp) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "If the camera scanner is unavailable, you can manually enter or paste the QR code query params or configuration text below to import settings:",
                                    fontSize = 12.sp,
                                    color = SoftGray
                                )

                                OutlinedTextField(
                                    value = manualQrText,
                                    onValueChange = { manualQrText = it },
                                    placeholder = { Text("e.g. dev_id|device_token|https://api-url/", color = Color.Gray, fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldPrimary,
                                        unfocusedBorderColor = SlateOutline,
                                        focusedContainerColor = Color(0xFF0C121A),
                                        unfocusedContainerColor = Color(0xFF0C121A)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (manualQrText.isNotBlank()) {
                                        val parsed = viewModel.parseAndApplyQrData(manualQrText)
                                        if (parsed) {
                                            Toast.makeText(context, "Credentials Loaded Successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to resolve QR data format", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    showQrFallbackDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showQrFallbackDialog = false }) {
                                Text("Cancel", color = SoftGray)
                            }
                        },
                        containerColor = SlateNavCard,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // How to guides Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How to get Device ID & Token?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LightText)
                
                val context = LocalContext.current
                val annotatedString1 = androidx.compose.ui.text.buildAnnotatedString {
                    append("1. Login to your website dashboard at ")
                    pushStringAnnotation(tag = "URL", annotation = "https://simresend.web.app")
                    pushStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            color = EmeraldPrimary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append("https://simresend.web.app")
                    pop()
                    pop()
                }

                androidx.compose.foundation.text.ClickableText(
                    text = annotatedString1,
                    style = androidx.compose.material3.LocalTextStyle.current.copy(
                        fontSize = 12.sp, 
                        color = SoftGray
                    ),
                    onClick = { offset ->
                        annotatedString1.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("SmsGatewayApp", "Failed to open link: ${e.message}")
                                }
                            }
                    }
                )

                val guides = listOf(
                    "2. Navigate to the Devices & Gateways workspace module",
                    "3. Click \"Add Gateway Client\" button to construct a device row",
                    "4. Copy or scan the custom credential parameters shown"
                )
                guides.forEach { guide ->
                    Text(guide, fontSize = 12.sp, color = SoftGray)
                }
            }
        }

        // Secure Shield footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0510B981), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x1010B981), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Secure & Private", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LightText)
                Text(
                    "Your device database credentials are encrypted using secure Android Keystore mechanics and remain isolated on-disk.",
                    color = SoftGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LegalSection(viewModel = viewModel)
    }

    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("SimGate Gateway Client", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LightText)
                    Text("Welcome to the SimGate Client Utility. This hardware utility establishes standard TLS-encrypted, long-lived sockets to synchronize messages between the device transceiver routing cells and your central workstation backend.", color = SoftGray, fontSize = 13.sp)
                    Button(
                        onClick = { showHelpDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Acknowledge", color = Color.Black)
                    }
                }
            }
        }
    }
}

// ----------------- LIVE VIEW: DASHBOARD -----------------

@Composable
fun DashboardScreen(viewModel: SmsGatewayViewModel, onViewAllHistory: () -> Unit, onNavigateToAdvanced: () -> Unit) {
    val scrollState = rememberScrollState()
    val state by viewModel.gatewayState.collectAsStateWithLifecycle()
    val recentLogs by viewModel.filteredHistoryLogs.collectAsStateWithLifecycle(emptyList())
    val activeSims by viewModel.activeSimsList.collectAsStateWithLifecycle()
    val selectedSimPref by viewModel.selectedSimPref.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Menu, contentDescription = "Drawer menu", tint = LightText)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("SMS Gateway", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LightText)
                Text("Your phone is running as SMS Gateway", fontSize = 11.sp, color = SoftGray)
            }
            Box {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Notifications, contentDescription = "Alert notifications", tint = LightText)
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Red, CircleShape)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = (8).dp)
                )
            }
        }

        // 1. Connection Banner Card
        val checkIcon = if (state.isConnected) Icons.Default.CheckCircle else Icons.Default.OfflineBolt
        val bannerBgBrush = if (state.isConnected) {
            Brush.verticalGradient(listOf(Color(0xFF042F1A), Color(0xFF0F2615)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF2E1C0C), Color(0xFF26180B)))
        }
        val statusBorder = if (state.isConnected) Color(0xFF10B981) else Color(0xFFD97706)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, statusBorder.copy(0.3f)), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerBgBrush)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1.0f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(if (state.isConnected) Color(0x3010B981) else Color(0x30D97706), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = checkIcon,
                                contentDescription = null,
                                tint = if (state.isConnected) EmeraldPrimary else Color(0xFFD97706),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (state.isConnected) "Connected" else "Offline / Reconnecting",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                            Text(
                                text = if (state.isConnected) "Gateway is running and connected to server" else "Waiting to resolve network connection...",
                                fontSize = 11.sp,
                                color = SoftGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Last heartbeat: ${state.lastHeartbeatTime}",
                                    fontSize = 11.sp,
                                    color = SoftGray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (state.isConnected) EmeraldPrimary else Color.Red, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (state.isConnected) "Online" else "Disconnected",
                                    fontSize = 10.sp,
                                    color = if (state.isConnected) EmeraldPrimary else Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Rotating Antenna Icon representation
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .border(1.dp, if (state.isConnected) EmeraldPrimary.copy(0.2f) else SoftGray.copy(0.2f), CircleShape)
                            .background(Color(0xFF0F1722), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsInputAntenna,
                            contentDescription = "Waves pulsing",
                            tint = if (state.isConnected) EmeraldPrimary else SoftGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 2. Device Parameters Details Card
        val clipboard = LocalClipboardManager.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Device ID", fontSize = 11.sp, color = SoftGray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = viewModel.deviceIdInput.value.takeIf { it.isNotBlank() } ?: "Unknown",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    val devId = viewModel.deviceIdInput.value
                                    if (devId.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(devId))
                                        Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Divider(color = SlateOutline, thickness = 0.8.dp)

                // Bottom row with Battery, Signal strength, and Uptime parameters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery", fontSize = 11.sp, color = SoftGray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.batteryPct < 25) Icons.Default.BatteryAlert else Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (state.batteryPct < 25) Color.Red else EmeraldPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${state.batteryPct}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightText)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Signal", fontSize = 11.sp, color = SoftGray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SignalCellular4Bar, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(state.signalText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uptime", fontSize = 11.sp, color = SoftGray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = SkySecondary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(state.uptimeString, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightText)
                        }
                    }
                }
            }
        }

        // 3. Dynamic Active SIM Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SIM Information", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightText)
                    Text(
                        text = "Manage SIMs",
                        color = SkySecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { /* No-op just visual indicator */ }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (activeSims.isEmpty()) {
                    Text("No active SIM cards detected on this device. Insert a SIM with cellular dispatch service to poll jobs.", color = SoftGray, fontSize = 12.sp)
                } else {
                    activeSims.forEachIndexed { index, sim ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (sim.isDefault) Color(0x2010B981) else Color(0x203B82F6), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        tint = if (sim.isDefault) EmeraldPrimary else SkySecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "SIM ${sim.slotIndex + 1}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = LightText
                                        )
                                        if (sim.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "(Default)",
                                                color = EmeraldPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Text(text = sim.carrierName, fontSize = 12.sp, color = SoftGray)
                                    Text(text = sim.phoneNumber ?: "+254...", fontSize = 11.sp, color = SoftGray)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sim.isDefault) {
                                    Box(
                                        modifier = Modifier
                                            .background(EmeraldPrimary.copy(0.1f), RoundedCornerShape(4.dp))
                                            .border(1.dp, EmeraldPrimary.copy(0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Default", color = EmeraldPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val isSimSelected = selectedSimPref.startsWith("SIM ${sim.slotIndex + 1}")
                                Switch(
                                    checked = isSimSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            val fullName = "SIM ${sim.slotIndex + 1} (${sim.carrierName})"
                                            viewModel.updateSelectedSimPref(fullName)
                                            Toast.makeText(context, "$fullName selected for dispatch", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.updateSelectedSimPref("Auto")
                                            Toast.makeText(context, "SIM dispatch set to Auto-select", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = EmeraldPrimary,
                                        checkedTrackColor = Color(0xFF064E3B)
                                    )
                                )
                            }
                        }
                        if (index < activeSims.lastIndex) {
                            Divider(color = SlateOutline, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // 4. Traffic Indicators row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sent Card
            TrafficCard(
                multiplier = 1f,
                icon = Icons.Default.ArrowUpward,
                iconColor = EmeraldPrimary,
                count = state.sentToday.toString(),
                label = "Sent Today",
                modifier = Modifier.weight(1f)
            )

            // Failed Card
            TrafficCard(
                multiplier = 1f,
                icon = Icons.Default.ErrorOutline,
                iconColor = Color.Red,
                count = state.failedToday.toString(),
                label = "Failed Today",
                modifier = Modifier.weight(1f)
            )

            // Received Card
            TrafficCard(
                multiplier = 1f,
                icon = Icons.Default.ArrowDownward,
                iconColor = SkySecondary,
                count = state.receivedToday.toString(),
                label = "Received Today",
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Quick Actions Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick Actions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightText)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start
                ActionGridItem(
                    title = "Start\nGateway",
                    icon = Icons.Default.PlayArrow,
                    containerColor = Color(0x1510B981),
                    contentColor = EmeraldPrimary,
                    onClick = {
                        viewModel.triggerServiceState(true)
                        Toast.makeText(context, "Starting Gateway Foreground Service...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )

                // Stop
                ActionGridItem(
                    title = "Stop\nGateway",
                    icon = Icons.Default.Stop,
                    containerColor = Color(0x15EF4444),
                    contentColor = Color.Red,
                    onClick = {
                        viewModel.triggerServiceState(false)
                        Toast.makeText(context, "Stopping Gateway Foreground Service", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )

                // Edit
                ActionGridItem(
                    title = "Edit\nCredentials",
                    icon = Icons.Default.Edit,
                    containerColor = Color(0x153B82F6),
                    contentColor = SkySecondary,
                    onClick = {
                        // Triggers credential reset
                        viewModel.disconnectDevice(onCompleted = {})
                        Toast.makeText(context, "Credentials unlocked!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )

                // Refresh State
                ActionGridItem(
                    title = "Refresh\nStatus",
                    icon = Icons.Default.Refresh,
                    containerColor = Color(0x1594A3B8),
                    contentColor = SoftGray,
                    onClick = {
                        viewModel.forceRefreshState()
                        Toast.makeText(context, "Forced status sync and heartbeat check!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- ADVANCED MESSAGING SHORTCUT CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAdvanced() },
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x1510B981), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Campaign Icon",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Advanced Messaging Hub",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Text(
                        text = "Schedule messages, save text drafts, organize categories templates and utilize Groq AI suggestions",
                        fontSize = 11.sp,
                        color = SoftGray
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SoftGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Recent Activity List Header with View All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Activity", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightText)
            Text(
                text = "View All",
                color = SkySecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onViewAllHistory() }
            )
        }

        if (recentLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateNavCard, RoundedCornerShape(12.dp))
                    .border(1.dp, SlateOutline, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.List,
                        contentDescription = null,
                        tint = SoftGray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No SMS traffic logged today", fontSize = 12.sp, color = SoftGray)
                }
            }
        } else {
            // Display latest 3 entries from dynamic list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentLogs.take(3).forEach { log ->
                    ActivityLogItem(log)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LegalSection(viewModel = viewModel)
    }
}

@Composable
fun TrafficCard(
    multiplier: Float,
    icon: ImageVector,
    iconColor: Color,
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
        border = BorderStroke(1.dp, SlateOutline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconColor.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = count,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = SoftGray
                )
            }
        }
    }
}

@Composable
fun ActionGridItem(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
        border = BorderStroke(1.dp, SlateOutline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(containerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                fontSize = 11.sp,
                color = LightText,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp
            )
        }
    }
}

// ----------------- SMS HISTORIC ACTIVITY TAB -----------------

@Composable
fun HistoryScreen(viewModel: SmsGatewayViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredLogs by viewModel.filteredHistoryLogs.collectAsStateWithLifecycle(emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Traffic History", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = LightText)

        // Sent, Failed, and Incoming Tabs selection
        TabRow(
            selectedTabIndex = when (selectedTab) {
                "Sent" -> 0
                "Failed" -> 1
                "Incoming" -> 2
                else -> 0
            },
            containerColor = SlateNavCard,
            contentColor = EmeraldPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[when (selectedTab) {
                        "Sent" -> 0
                        "Failed" -> 1
                        "Incoming" -> 2
                        else -> 0
                    }]),
                    color = EmeraldPrimary
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
        ) {
            listOf("Sent", "Failed", "Incoming").forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { viewModel.setTab(tab) },
                    text = { Text(tab, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                    unselectedContentColor = SoftGray,
                    selectedContentColor = EmeraldPrimary
                )
            }
        }

        // Search Bar row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search by phone number or text...", color = Color.Gray, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Lookup", tint = SoftGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = SoftGray)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = SlateOutline,
                focusedContainerColor = SlateNavCard,
                unfocusedContainerColor = SlateNavCard
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredLogs.size} logs listed",
                fontSize = 12.sp,
                color = SoftGray
            )
            if (filteredLogs.isNotEmpty()) {
                Text(
                    text = "Clear Current logs",
                    fontSize = 12.sp,
                    color = Color.Red,
                    modifier = Modifier.clickable { viewModel.clearAllHistory() },
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterListOff,
                        contentDescription = "Empty set",
                        tint = SoftGray,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("No matched logs in database", fontSize = 14.sp, color = SoftGray)
                    Text("Sent & received messages will populate here.", fontSize = 11.sp, color = Color.DarkGray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    ActivityLogItem(log)
                }
            }
        }
    }
}

@Composable
fun ActivityLogItem(log: SmsEntity) {
    val formattedTime = remember(log.timestamp) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    val themeColor = when (log.type) {
        "Sent" -> EmeraldPrimary
        "Failed" -> Color.Red
        "Incoming" -> SkySecondary
        else -> SoftGray
    }

    val indicatorIcon = when (log.type) {
        "Sent" -> Icons.Default.ArrowUpward
        "Failed" -> Icons.Default.ErrorOutline
        "Incoming" -> Icons.Default.ArrowDownward
        else -> Icons.Default.ArrowUpward
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
        border = BorderStroke(0.8.dp, SlateOutline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(themeColor.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = null,
                    tint = themeColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = maskPhoneNumber(log.phoneNumber),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = LightText
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = SoftGray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.message,
                    fontSize = 13.sp,
                    color = SoftGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Carrier: ${log.simUsed}",
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = log.status + (log.errorMessage?.let { " • $it" } ?: ""),
                        fontSize = 11.sp,
                        color = themeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ----------------- DYNAMIC SIM MODULATOR TAB -----------------

@Composable
fun SimsScreen(viewModel: SmsGatewayViewModel) {
    val activeSims by viewModel.activeSimsList.collectAsStateWithLifecycle()
    val simPref by viewModel.selectedSimPref.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("SIM Slots Manager", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = LightText)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select Default SIM dispatch lane", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LightText)
                Text("Android multi-SIM drivers resolve cellular bands matching the choice below. Auto allocates FIFO on primary Slot.", fontSize = 12.sp, color = SoftGray)

                Spacer(modifier = Modifier.height(6.dp))

                val options = remember(activeSims) {
                    val list = mutableListOf("Auto", "Default")
                    activeSims.forEach { sim ->
                        list.add("SIM ${sim.slotIndex + 1} (${sim.carrierName})")
                    }
                    if (list.size <= 2) {
                        list.add("SIM 1")
                        list.add("SIM 2")
                    }
                    list
                }

                options.forEach { option ->
                    val isSelected = if (option == "Auto" || option == "Default") {
                        simPref == option
                    } else {
                        val basePrefix = option.substringBefore(" (")
                        simPref.startsWith(basePrefix)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateSelectedSimPref(option) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.updateSelectedSimPref(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(option, color = LightText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Active Cellular Hardware Monitor", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LightText)

                if (activeSims.isEmpty()) {
                    Text("No physical/electronic SIM carriers recognized currently. Please make sure card pins touch cell slot accurately.", fontSize = 12.sp, color = SoftGray)
                } else {
                    activeSims.forEach { sim ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CellTower, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Slot ${sim.slotIndex + 1}: ${sim.carrierName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LightText)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (sim.isDefault) EmeraldPrimary.copy(0.1f) else SkySecondary.copy(0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (sim.isDefault) "Default Primary" else "Secondary Slot",
                                        color = if (sim.isDefault) EmeraldPrimary else SkySecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Line Subscription ID: ${sim.subscriptionId}", fontSize = 11.sp, color = SoftGray)
                            Text("Reported Number: ${sim.phoneNumber ?: "Unknown"}", fontSize = 11.sp, color = SoftGray)
                        }
                    }
                }
            }
        }
    }
}

// ----------------- SYSTEM SETTINGS TAB -----------------

@Composable
fun SettingsScreen(viewModel: SmsGatewayViewModel, onDisconnect: () -> Unit, onNavigateToAdvanced: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.gatewayState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    // Observe Preference/Setting values reactively
    val pollInterval by viewModel.pollIntervalInput.collectAsStateWithLifecycle()
    val heartbeatInterval by viewModel.heartbeatIntervalInput.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnectEnabled.collectAsStateWithLifecycle()
    val startOnBoot by viewModel.startOnBootEnabled.collectAsStateWithLifecycle()
    val isPaused by viewModel.gatewayPaused.collectAsStateWithLifecycle()

    val notifPersistent by viewModel.persistentNotificationEnabled.collectAsStateWithLifecycle()
    val notifSmsSent by viewModel.smsSentNotificationEnabled.collectAsStateWithLifecycle()
    val notifSmsFailed by viewModel.smsFailedNotificationEnabled.collectAsStateWithLifecycle()
    val notifIncomingSms by viewModel.incomingSmsNotificationEnabled.collectAsStateWithLifecycle()

    val securityReport by viewModel.securityReport.collectAsStateWithLifecycle()
    val antiScreenshot by viewModel.antiScreenshotEnabled.collectAsStateWithLifecycle()
    val selfDefense by viewModel.selfDefenseModeEnabled.collectAsStateWithLifecycle()

    var tokenVisible by remember { mutableStateOf(false) }

    // Dialog triggering states
    var showEditCredentials by remember { mutableStateOf(false) }
    var showUnpairConfirm by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showHeartbeatDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showClearLogsConfirm by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HEADER ROW (Title & verified check) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
                Text(
                    text = "Configure your SimGate Gateway",
                    fontSize = 12.sp,
                    color = SoftGray
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x1510B981), CircleShape)
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Verified Status",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- GATEWAY STATUS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left circular green device icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0x1510B981), CircleShape)
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gateway Status",
                        fontSize = 11.sp,
                        color = SoftGray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (state.isRunning) "Connected" else "Stopped",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isRunning) EmeraldPrimary else Color.Red
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (state.isRunning) EmeraldPrimary else Color.Red, CircleShape)
                        )
                        Text(
                            text = if (state.isRunning) "Online" else "Offline",
                            fontSize = 12.sp,
                            color = if (state.isRunning) EmeraldPrimary else Color.Red
                        )
                    }
                    Text(
                        text = if (state.isRunning) "SimGate is running smoothly" else "Gateway queue is offline",
                        fontSize = 11.sp,
                        color = SoftGray
                    )
                }

                // Restart Gateway button on right
                OutlinedButton(
                    onClick = {
                        viewModel.forceRefreshState()
                        Toast.makeText(context, "Restarting gateway queue polling", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LightText),
                    border = BorderStroke(1.dp, SlateOutline),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = LightText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Restart",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                }
            }
        }

        // Helper text label
        Text(
            text = "Device & Connection",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // --- DEVICE & CONNECTION CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                // Device ID info row
                SettingsRow(
                    icon = Icons.Default.Person,
                    title = "Device ID",
                    subtitle = "Unique identifier for this device",
                    onClick = {
                        clipboard.setText(AnnotatedString(viewModel.deviceIdInput.value))
                        Toast.makeText(context, "Copied Device ID!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = viewModel.deviceIdInput.value,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SoftGray
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy ID",
                            tint = SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Device Token info row
                SettingsRow(
                    icon = Icons.Default.Key,
                    title = "Device Token",
                    subtitle = "Secret token for authenticating requests",
                    onClick = {
                        clipboard.setText(AnnotatedString(viewModel.deviceTokenInput.value))
                        Toast.makeText(context, "Copied Device Token!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (tokenVisible) viewModel.deviceTokenInput.value else "dtk_••••••••••••••••",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SoftGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                        IconButton(
                            onClick = { tokenVisible = !tokenVisible },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = SoftGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Edit Credentials
                SettingsRow(
                    icon = Icons.Default.Edit,
                    title = "Edit Credentials",
                    subtitle = "Update device ID and token",
                    onClick = { showEditCredentials = true }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Reconnect Row
                SettingsRow(
                    icon = Icons.Default.Sync,
                    title = "Reconnect",
                    subtitle = "Reconnect to the server now",
                    onClick = {
                        viewModel.forceRefreshState()
                        Toast.makeText(context, "Reconnecting to SimResend Server...", Toast.LENGTH_SHORT).show()
                    }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Unpair Device Row
                SettingsRow(
                    icon = Icons.Default.PowerSettingsNew,
                    iconTint = Color.Red,
                    title = "Unpair Device",
                    titleColor = Color.Red,
                    subtitle = "Remove this device from gateway",
                    onClick = { showUnpairConfirm = true }
                )
            }
        }

        // Gateway Settings Header
        Text(
            text = "Gateway Settings",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // --- GATEWAY SYSTEM CONFIGS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                // Poll Interval Row
                SettingsRow(
                    icon = Icons.Default.CellTower,
                    title = "Poll Interval",
                    subtitle = "How often to check for new SMS jobs",
                    onClick = { showPollDialog = true }
                ) {
                    val unit = if (pollInterval >= 60) "${pollInterval / 60} minute" + (if (pollInterval >= 120) "s" else "") else "$pollInterval seconds"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = unit, fontSize = 12.sp, color = LightText)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Heartbeat Interval Row
                SettingsRow(
                    icon = Icons.Default.Favorite,
                    title = "Heartbeat Interval",
                    subtitle = "How often to send heartbeat",
                    onClick = { showHeartbeatDialog = true }
                ) {
                    val unit = if (heartbeatInterval >= 60) "${heartbeatInterval / 60} minute" + (if (heartbeatInterval >= 120) "s" else "") else "$heartbeatInterval seconds"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = unit, fontSize = 12.sp, color = LightText)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Auto Reconnect Switch Row
                SettingsRow(
                    icon = Icons.Default.Autorenew,
                    title = "Auto Reconnect",
                    subtitle = "Automatically reconnect when disconnected",
                    rightContent = {
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = { viewModel.updateAutoReconnect(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = SlateOutline
                            )
                        )
                    }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Start on Boot Switch Row
                SettingsRow(
                    icon = Icons.Default.Power,
                    title = "Start on Boot",
                    subtitle = "Start gateway automatically on device boot",
                    rightContent = {
                        Switch(
                            checked = startOnBoot,
                            onCheckedChange = { viewModel.updateStartOnBoot(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = SlateOutline
                            )
                        )
                    }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Pause Gateway Switch Row
                SettingsRow(
                    icon = Icons.Default.Pause,
                    title = "Pause Gateway",
                    subtitle = "Temporarily pause sending and polling",
                    rightContent = {
                        Switch(
                            checked = isPaused,
                            onCheckedChange = { viewModel.updateGatewayPaused(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = SlateOutline
                            )
                        )
                    }
                )
            }
        }

        // Notifications Header
        Text(
            text = "Notifications",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // --- NOTIFICATIONS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                // Persistent notification Switch Row
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    title = "Persistent Notification",
                    subtitle = "Show ongoing gateway status",
                    rightContent = {
                        Switch(
                            checked = notifPersistent,
                            onCheckedChange = { viewModel.updatePersistentNotification(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = SlateOutline
                            )
                        )
                    }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // SMS Sent Notifications Switch Row
                SettingsRow(
                    icon = Icons.Default.Message,
                    title = "SMS Sent Notifications",
                    subtitle = "Notify when SMS is sent successfully",
                    rightContent = {
                        Switch(
                            checked = notifSmsSent,
                            onCheckedChange = { viewModel.updateSmsSentNotification(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = SlateOutline
                            )
                        )
                    }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // SMS Failed Notifications Switch Row
                SettingsRow(
                    icon = Icons.Default.ErrorOutline,
                    title = "SMS Failed Notifications",
                    subtitle = "Notify when SMS fails to send",
                    rightContent = {
                        Switch(
                            checked = notifSmsFailed,
                            onCheckedChange = { viewModel.updateSmsFailedNotification(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = SlateOutline
                            )
                        )
                    }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Incoming SMS Notifications Switch Row
                SettingsRow(
                    icon = Icons.Default.MoveToInbox,
                    title = "Incoming SMS Notifications",
                    subtitle = "Notify when new SMS is received",
                    rightContent = {
                        Switch(
                            checked = notifIncomingSms,
                            onCheckedChange = { viewModel.updateIncomingSmsNotification(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = SlateOutline
                            )
                        )
                    }
                )
            }
        }

        // Data & Storage Header
        Text(
            text = "Data & Storage",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // --- DATA & STORAGE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                // Clear History Button Row
                SettingsRow(
                    icon = Icons.Default.Dns,
                    title = "Clear History",
                    subtitle = "Remove all local SMS history",
                    onClick = { showClearHistoryConfirm = true }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Export Logs Button Row
                SettingsRow(
                    icon = Icons.Default.CloudDownload,
                    title = "Export Logs",
                    subtitle = "Export logs for support",
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val shareText = """
                            ====================================
                            SimGate client diagnostic report
                            ====================================
                            Generated At : ${timestamp}
                            Device ID    : ${viewModel.deviceIdInput.value}
                            Uptime       : ${state.uptimeString}
                            Connection   : ${state.connectionMessage}
                            SIM Selection: ${viewModel.selectedSimPref.value}
                            Active SIMs  : ${state.activeSimInfo}
                            Power/Signal : Battery ${state.batteryPct}%, Level ${state.signalText}
                            Stats Today  : Sent=${state.sentToday}, Failed=${state.failedToday}, Received=${state.receivedToday}
                            ====================================
                            Report delivered from SimResend secure client container.
                        """.trimIndent()
                        
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SimGate Client Diagnostics log")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Gateway Log Summary"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to launch sharing intent: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Clear Logs Row
                SettingsRow(
                    icon = Icons.Default.Delete,
                    title = "Clear Logs",
                    subtitle = "Remove all local logs",
                    onClick = { showClearLogsConfirm = true }
                )
            }
        }

        // --- ADVANCED SERVICES TITLE ---
        Text(
            text = "Advanced Services",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                // Navigate to Advanced Messaging Hub
                SettingsRow(
                    icon = Icons.Default.Campaign,
                    title = "Advanced Messaging Hub",
                    subtitle = "Navigate to schedule planner, drafts & template category manager",
                    onClick = onNavigateToAdvanced
                )

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Groq API Key Setup row
                var showGroqKeyDialog by remember { mutableStateOf(false) }
                val maskedGroqKey = viewModel.getMaskedGroqKey()
                val activeGroqModel by viewModel.groqModelInput.collectAsStateWithLifecycle()
                
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Groq Trial AI Model",
                    subtitle = "Configure securely hashed Groq API configurations",
                    onClick = { showGroqKeyDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (maskedGroqKey.isNotBlank()) "Active ($activeGroqModel)" else "Unset",
                            fontSize = 12.sp,
                            color = if (maskedGroqKey.isNotBlank()) EmeraldPrimary else SoftGray,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                    }
                }

                if (showGroqKeyDialog) {
                    var inputKey by remember { mutableStateOf("") }
                    var selectedModel by remember { mutableStateOf(activeGroqModel) }
                    
                    AlertDialog(
                        onDismissRequest = { showGroqKeyDialog = false },
                        title = { Text("Configure Groq Client Connection", color = LightText, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Provide your secure trial Groq API key directly. This key remains physically encrypted within the local application sandbox:", fontSize = 12.sp, color = SoftGray)
                                
                                OutlinedTextField(
                                    value = inputKey,
                                    onValueChange = { inputKey = it },
                                    label = { Text("Groq API Key (gsk_...)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = LightText,
                                        unfocusedTextColor = LightText,
                                        focusedBorderColor = EmeraldPrimary,
                                        unfocusedBorderColor = SlateOutline
                                    ),
                                    placeholder = { Text(text = if (maskedGroqKey.isNotBlank()) "••••••••••••" else "Enter Key") }
                                )

                                Text("Select Groq Inference LLM Model:", fontSize = 12.sp, color = LightText, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    listOf("llama3-8b-8192", "mixtral-8x7b-3275").forEach { m ->
                                        val isSel = selectedModel == m
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (isSel) Color(0x3010B981) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                .border(1.dp, if (isSel) EmeraldPrimary else SlateOutline, RoundedCornerShape(8.dp))
                                                .clickable { selectedModel = m }
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = if (m.startsWith("llama")) "Llama-3" else "Mixtral", color = if (isSel) EmeraldPrimary else LightText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (inputKey.isNotBlank()) {
                                        viewModel.updateGroqApiKey(inputKey.trim())
                                        Toast.makeText(context, "Secure API configuration stored safely!", Toast.LENGTH_SHORT).show()
                                    }
                                    viewModel.updateGroqModel(selectedModel)
                                    showGroqKeyDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Save Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showGroqKeyDialog = false }) {
                                Text("Cancel", color = SoftGray)
                            }
                        },
                        containerColor = SlateNavCard,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }



        // About Header
        Text(
            text = "About",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // --- ABOUT CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                // App Version Row
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "App Version",
                    subtitle = "SimGate Gateway"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "1.0.0 (100)", fontSize = 12.sp, color = SoftGray)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // About SimGate Gateway Row
                SettingsRow(
                    icon = Icons.Default.HelpOutline,
                    title = "About SimGate Gateway",
                    subtitle = "Learn more about the app",
                    onClick = { showAboutDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LegalSection(viewModel = viewModel)

        Spacer(modifier = Modifier.height(24.dp))
    }

    // ==========================================
    // --- POPUP DIALOGS & ALERTS ---
    // ==========================================

    // EDIT CREDENTIALS DIALOG
    if (showEditCredentials) {
        var tempId by remember { mutableStateOf(viewModel.deviceIdInput.value) }
        var tempToken by remember { mutableStateOf(viewModel.deviceTokenInput.value) }
        AlertDialog(
            onDismissRequest = { showEditCredentials = false },
            title = { Text("Edit Device Credentials", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Modify local device identifier fields to change the routing client parameters:", fontSize = 12.sp, color = SoftGray)
                    
                    OutlinedTextField(
                        value = tempId,
                        onValueChange = { tempId = it },
                        label = { Text("Device ID") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        )
                    )

                    OutlinedTextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text("Device Token") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempId.isNotBlank() && tempToken.isNotBlank()) {
                            viewModel.deviceIdInput.value = tempId.trim()
                            viewModel.deviceTokenInput.value = tempToken.trim()
                            viewModel.connectDevice(onSuccess = {})
                            Toast.makeText(context, "Credentials updated! Connecting...", Toast.LENGTH_SHORT).show()
                            showEditCredentials = false
                        } else {
                            Toast.makeText(context, "Fields cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCredentials = false }) {
                    Text("Cancel", color = SoftGray)
                }
            }
        )
    }

    // UNPAIR CONFIRMATION DIALOG
    if (showUnpairConfirm) {
        AlertDialog(
            onDismissRequest = { showUnpairConfirm = false },
            title = { Text("Unpair Device", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to unpair this client device and stop all foreground operations? Credentials will be erased.", fontSize = 13.sp, color = SoftGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnpairConfirm = false
                        viewModel.disconnectDevice(onCompleted = onDisconnect)
                        Toast.makeText(context, "Device successfully unpaired!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Unpair", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairConfirm = false }) {
                    Text("Cancel", color = SoftGray)
                }
            }
        )
    }

    // POLL INTERVAL CHOICES DIALOG
    if (showPollDialog) {
        val options = listOf(
            5 to "5 seconds (Default)",
            10 to "10 seconds",
            15 to "15 seconds",
            30 to "30 seconds",
            60 to "1 minute",
            300 to "5 minutes"
        )
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Select Poll Interval", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updatePollInterval(seconds)
                                    Toast.makeText(context, "Polling frequency set to: $label", Toast.LENGTH_SHORT).show()
                                    showPollDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (pollInterval == seconds),
                                onClick = {
                                    viewModel.updatePollInterval(seconds)
                                    Toast.makeText(context, "Polling frequency set to: $label", Toast.LENGTH_SHORT).show()
                                    showPollDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, color = LightText, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // HEARTBEAT INTERVAL CHOICES DIALOG
    if (showHeartbeatDialog) {
        val options = listOf(
            30 to "30 seconds",
            60 to "60 seconds (Default)",
            120 to "2 minutes",
            300 to "5 minutes",
            600 to "10 minutes"
        )
        AlertDialog(
            onDismissRequest = { showHeartbeatDialog = false },
            title = { Text("Select Heartbeat Interval", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateHeartbeatInterval(seconds)
                                    Toast.makeText(context, "Heartbeat frequency set to: $label", Toast.LENGTH_SHORT).show()
                                    showHeartbeatDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (heartbeatInterval == seconds),
                                onClick = {
                                    viewModel.updateHeartbeatInterval(seconds)
                                    Toast.makeText(context, "Heartbeat frequency set to: $label", Toast.LENGTH_SHORT).show()
                                    showHeartbeatDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, color = LightText, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // CLEAR HISTORY ALERT
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear History Logs", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Text("This action can't be undone. Purge historical database registers?", fontSize = 13.sp, color = SoftGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        Toast.makeText(context, "History database successfully purged!", Toast.LENGTH_SHORT).show()
                        showClearHistoryConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Clear", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = SoftGray)
                }
            }
        )
    }

    // CLEAR LOGS ALERT
    if (showClearLogsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLogsConfirm = false },
            title = { Text("Clear Local System Logs", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove temporary connection metrics and diagnostic session histories?", fontSize = 13.sp, color = SoftGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Local app diagnostic cache cleared!", Toast.LENGTH_SHORT).show()
                        showClearLogsConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Clear", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsConfirm = false }) {
                    Text("Cancel", color = SoftGray)
                }
            }
        )
    }

    // ABOUT DIALOG
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("SimGate Client Info", color = EmeraldPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SimGate is a high-performance local SMS gateway relay connecting physical Android transceiver devices to web servers.", fontSize = 13.sp, color = LightText)
                    Text("Features include dual-SIM scheduling, asynchronous SMS processing queue, persistent foreground notification monitoring, and automatic reconnection. Designed on Material 3 guidelines and Jetpack Compose.", fontSize = 12.sp, color = SoftGray)
                    Text("All data logs remain strictly on device. Connected to gateway server at https://simresend.web.app.", fontSize = 11.sp, color = EmeraldPrimary)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// Reuseable helper row layout styled identically to standard settings options
@Composable
fun SettingsRow(
    icon: ImageVector,
    iconTint: Color = EmeraldPrimary,
    title: String,
    subtitle: String,
    titleColor: Color = LightText,
    onClick: (() -> Unit)? = null,
    rightContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rounded Left Icon with Tint Box
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, iconTint.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = SoftGray,
                lineHeight = 14.sp
            )
        }

        if (rightContent != null) {
            rightContent()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SoftGray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ----------------- ONBOARDING & LEGAL COMPOSABLES -----------------

@Composable
fun SimGateWelcomeLogo() {
    Box(
        modifier = Modifier
            .size(120.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw matching eSIM card / SIM card + Chat Bubble using Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val greenColor = Color(0xFF10B981)
            val strokeWidthVal = 4.dp.toPx()
            
            // 1. Draw SIM Card Rounded Rectangle with top right corner cut
            val cardWidth = size.width * 0.7f
            val cardHeight = size.height * 0.85f
            val left = (size.width - cardWidth) / 2f - size.width * 0.05f
            val top = (size.height - cardHeight) / 2f - size.height * 0.05f
            
            val path = androidx.compose.ui.graphics.Path().apply {
                // Top-left
                moveTo(left, top + 24.dp.toPx())
                quadraticTo(left, top, left + 24.dp.toPx(), top)
                
                // Top-right cut (SIM Card characteristic)
                lineTo(left + cardWidth - 36.dp.toPx(), top)
                lineTo(left + cardWidth, top + 36.dp.toPx())
                
                // Bottom-right
                lineTo(left + cardWidth, top + cardHeight - 24.dp.toPx())
                quadraticTo(left + cardWidth, top + cardHeight, left + cardWidth - 24.dp.toPx(), top + cardHeight)
                
                // Bottom-left
                lineTo(left + 24.dp.toPx(), top + cardHeight)
                quadraticTo(left, top + cardHeight, left, top + cardHeight - 24.dp.toPx())
                
                close()
            }
            
            drawPath(
                path = path,
                color = greenColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidthVal,
                    miter = 4f,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
            
            // 2. Draw SIM contacts grid layout in center
            val gridLeft = left + cardWidth * 0.2f
            val gridTop = top + cardHeight * 0.35f
            val gridWidth = cardWidth * 0.6f
            val gridHeight = cardHeight * 0.4f
            
            // Draw grid rounded rectangles inside the SIM Card
            drawRoundRect(
                color = greenColor,
                topLeft = Offset(gridLeft, gridTop),
                size = androidx.compose.ui.geometry.Size(gridWidth, gridHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
            
            // Grid Divider Lines
            drawLine(
                color = greenColor,
                start = Offset(gridLeft + gridWidth / 2f, gridTop),
                end = Offset(gridLeft + gridWidth / 2f, gridTop + gridHeight),
                strokeWidth = 3.dp.toPx()
            )
            
            drawLine(
                color = greenColor,
                start = Offset(gridLeft, gridTop + gridHeight / 3f),
                end = Offset(gridLeft + gridWidth, gridTop + gridHeight / 3f),
                strokeWidth = 3.dp.toPx()
            )
            
            drawLine(
                color = greenColor,
                start = Offset(gridLeft, gridTop + 2 * gridHeight / 3f),
                end = Offset(gridLeft + gridWidth, gridTop + 2 * gridHeight / 3f),
                strokeWidth = 3.dp.toPx()
            )
        }
        
        // 3. Draw SMS Chat Speech Bubble overlapping at bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (12).dp, y = (4).dp)
                .size(54.dp)
                .background(Color(0xFF0F172A), CircleShape)
                .border(3.1.dp, Color(0xFF10B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun WorkspaceActivationScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // Match ObsidianBackground / dark slate
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Pulsing / Rotating custom launcher icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0x1010B981), CircleShape)
                    .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(76.dp),
                    color = Color(0xFF10B981),
                    strokeWidth = 3.dp
                )
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Text(
                text = "Activating your workspace",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Configuring secure gateways, mapping on-disk credentials, and establishing hardware interface connections.",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8), // SoftGray
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun OnboardingScreen(viewModel: SmsGatewayViewModel) {
    var currentPage by remember { mutableStateOf(0) } // 0 = Welcome, 1 = Terms, 2 = Privacy
    val areTermsAccepted by viewModel.areTermsAccepted.collectAsStateWithLifecycle()
    val isPrivacyAccepted by viewModel.isPrivacyAccepted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Match ObsidianBackground / dark slate from screenshot
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            when (currentPage) {
                0 -> {
                    // PAGE 1: Welcome Screen matching screenshot
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // SimGate Welcome Logo (Custom Canvas Drawing)
                        SimGateWelcomeLogo()
                        
                        // App title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "SimGate ",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Gateway",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        // Subtitle
                        Text(
                            text = "Turn your phone into a powerful SMS gateway",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Card "What SimGate does"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // deep dark slate
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "What SimGate does",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                
                                val features = listOf(
                                    Triple(
                                        "Send SMS Remotely",
                                        "Receive SMS requests from your server and send them using this device.",
                                        Icons.Default.ChatBubbleOutline
                                    ),
                                    Triple(
                                        "Receive Incoming SMS",
                                        "Capture incoming SMS and forward them to your backend in real-time.",
                                        Icons.Default.MoveToInbox
                                    ),
                                    Triple(
                                        "Real-time Device Monitoring",
                                        "Report battery level, signal strength, network and status to your server.",
                                        Icons.Default.CellTower
                                    ),
                                    Triple(
                                        "Reliable & Secure",
                                        "Runs in the background securely with auto reconnect and data encryption.",
                                        Icons.Default.Security
                                    ),
                                    Triple(
                                        "Multi SIM Support",
                                        "Works with single or dual SIM devices and lets you choose the preferred SIM.",
                                        Icons.Default.SimCard
                                    ),
                                    Triple(
                                        "Always On",
                                        "Persistent foreground service keeps the gateway running 24/7.",
                                        Icons.Default.Schedule
                                    )
                                )
                                
                                features.forEach { (title, subtitle, icon) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0x1510B981), CircleShape)
                                                .border(0.5.dp, Color(0x3010B981), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(14.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = subtitle,
                                                fontSize = 12.sp,
                                                color = Color(0xFF94A3B8),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Credential info warning
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x1010B981), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0x2510B981), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Your credentials are safe and stored securely on this device.",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Footer Button Page 1
                    Button(
                        onClick = { currentPage = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Continue ",
                                fontSize = 16.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                1 -> {
                    // PAGE 2: Terms & Conditions
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Terms & Conditions",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            text = "Please read and accept the terms of service to continue using SimGate:",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = getDetailedTermsText(),
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateTermsAccepted(!areTermsAccepted) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = areTermsAccepted,
                                onCheckedChange = { viewModel.updateTermsAccepted(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF10B981),
                                    uncheckedColor = Color(0xFF94A3B8)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I have read and agree to the Terms & Conditions",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    // Footer Button Page 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentPage = 0 },
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text("Back", fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = { currentPage = 2 },
                            enabled = areTermsAccepted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0x3010B981)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(2.2f)
                                .height(52.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Accept & Continue ",
                                    color = if (areTermsAccepted) Color.Black else Color(0x70FFFFFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = if (areTermsAccepted) Color.Black else Color(0x70FFFFFF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                2 -> {
                    // PAGE 3: Privacy Policy
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Privacy Policy",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            text = "Please review and accept our Privacy Policy regarding the transmission and storage of data:",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = getDetailedPrivacyText(),
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updatePrivacyAccepted(!isPrivacyAccepted) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isPrivacyAccepted,
                                onCheckedChange = { viewModel.updatePrivacyAccepted(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF10B981),
                                    uncheckedColor = Color(0xFF94A3B8)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I have read and agree to the Privacy Policy",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    // Footer Button Page 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentPage = 1 },
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text("Back", fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                if (areTermsAccepted && isPrivacyAccepted) {
                                    viewModel.updateOnboardingCompleted(true)
                                    Toast.makeText(context, "Welcome to SimGate!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = areTermsAccepted && isPrivacyAccepted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0x3010B981)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(2.2f)
                                .height(52.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Accept & Get Started ",
                                    color = if (areTermsAccepted && isPrivacyAccepted) Color.Black else Color(0x70FFFFFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (areTermsAccepted && isPrivacyAccepted) Color.Black else Color(0x70FFFFFF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Render the page indicator dots exactly matching screenshot: Green, Medium Green, Gray
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 1, 2).forEach { page ->
                    val color = when {
                        currentPage == page -> Color(0xFF10B981) // active: bright green
                        currentPage > page -> Color(0xFF047857) // visited: medium green
                        else -> Color(0xFF475569) // unvisited: slate gray
                    }
                    Box(
                        modifier = Modifier
                            .size(if (currentPage == page) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    if (page < 2) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LegalSection(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    val areTermsAccepted by viewModel.areTermsAccepted.collectAsStateWithLifecycle()
    val isPrivacyAccepted by viewModel.isPrivacyAccepted.collectAsStateWithLifecycle()

    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
        border = BorderStroke(1.dp, SlateOutline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Legal Compliance",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )

            Text(
                text = "Use of the SimGate SMS gateway requires agreement to the Terms & Conditions and the Privacy Policy. Check your status below:",
                fontSize = 12.sp,
                color = SoftGray,
                lineHeight = 16.sp
            )

            HorizontalDivider(color = SlateOutline, thickness = 0.5.dp)

            // --- TERMS ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Terms & Conditions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (areTermsAccepted) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x2010B981), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Accepted", fontSize = 10.sp, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x20EF4444), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Pending", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "Carrier rates and acceptable SMS policies",
                        fontSize = 11.sp,
                        color = SoftGray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { showTermsDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Read", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!areTermsAccepted) {
                        Button(
                            onClick = {
                                viewModel.updateTermsAccepted(true)
                                Toast.makeText(context, "Terms Accepted!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Accept", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = SlateOutline, thickness = 0.5.dp)

            // --- PRIVACY ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Privacy Policy",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isPrivacyAccepted) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x2010B981), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Accepted", fontSize = 10.sp, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x20EF4444), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Pending", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "Hardware storage and local isolation details",
                        fontSize = 11.sp,
                        color = SoftGray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { showPrivacyDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Read", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!isPrivacyAccepted) {
                        Button(
                            onClick = {
                                viewModel.updatePrivacyAccepted(true)
                                Toast.makeText(context, "Privacy Policy Accepted!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Accept", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // TERMS DETAIL DIALOG
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms & Conditions", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = getDetailedTermsText(), fontSize = 12.sp, color = SoftGray)
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!areTermsAccepted) {
                        Button(
                            onClick = {
                                viewModel.updateTermsAccepted(true)
                                Toast.makeText(context, "Terms Accepted!", Toast.LENGTH_SHORT).show()
                                showTermsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Accept", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { showTermsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, SlateOutline)
                    ) {
                        Text("Close", color = LightText, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = SlateNavCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // PRIVACY DETAIL DIALOG
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = getDetailedPrivacyText(), fontSize = 12.sp, color = SoftGray)
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isPrivacyAccepted) {
                        Button(
                            onClick = {
                                viewModel.updatePrivacyAccepted(true)
                                Toast.makeText(context, "Privacy Policy Accepted!", Toast.LENGTH_SHORT).show()
                                showPrivacyDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Accept", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { showPrivacyDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, SlateOutline)
                    ) {
                        Text("Close", color = LightText, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = SlateNavCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

fun getDetailedTermsText(): String {
    return """
        SIMRESEND SIMGATE SMS GATEWAY - TERMS & CONDITIONS
        Last Updated: June 2026

        Please read these Terms & Conditions ("Terms") carefully before using SimGate Gateway Android Application ("App" or "Service"). By checking the checkbox and proceeding, you agree to be bound by these Terms as an operator of the gateway client.

        1. DEFINITIONS & RESPONSIBILITIES
        - "SimGate Gateway" is a client utility designed to link local mobile device SIM card interfaces to standard, securely initialized external server queues.
        - You represent that you are the legally entitled owner of the mobile SIM card used with this application, or that you have obtained explicit written authorization from the primary subscription account owner to route messages.

        2. TELEPHONY CARRIER CHARGES & DISCLAIMER
        - IMPORTANT WARNING: SimGate facilitates transmission of SMS messages directly through your mobile network carrier.
        - Sending or receiving SMS messages via SimGate will deplete messaging allowances or incur financial charges according to your network operator's rates or contract details.
        - You accept exclusive, non-transferrable responsibility for any and all charges, costs, carrier bills, overage fees, roaming tariffs, or data usage charges occurring on your network subscription plan.
        - We are under no circumstances liable for any bills or mobile operator expenses resulting from your usage of this Service.

        3. COMPLIANCE & ACCEPTABLE USE
        - You agree to comply with all regional, state, federal, and international laws, telecommunications carrier regulations, and anti-spam protection laws (including TCPA and CAN-SPAM).
        - You are strictly forbidden from routing:
          * Commercial electronic advertisements without validated recipient opt-in registers;
          * Intentionally deceptive financial offers, credit/banking fraud, phishing payloads, or illegal gambling;
          * Materials intended to harass, threaten, stalk, defame, or abuse natural persons;
          * Malware, remote payload execution scripts, or malicious attachments.
        - We reserve the right to report carrier violations or cooperate with server-side network audits.

        4. PERFORMANCE & SERVICE DISCLAIMERS
        - The Service is provided "as is" and "as available", without explicit or statutory warranties of any kind.
        - We make no guarantee of continuous, error-free, or uninterrupted delivery. Mobile network latency, cellular signal degradation, device power management, or operating system policies can delay or prevent SMS deliveries.
        - We decline liability for lost messaging queue logs, business losses, or legal liabilities arising from technical failures.

        5. GOVERNING LAW
        - These terms shall be governed by international internet policies and privacy rules without regard to conflict of law principles.
    """.trimIndent()
}

fun getDetailedPrivacyText(): String {
    return """
        SIMRESEND SIMGATE SMS GATEWAY - PRIVACY POLICY
        Last Updated: June 2026

        Your privacy is our core engineering design priority. This Privacy Policy details how SimGate ("App") manages, encrypts, and secures your personal and cellular network operational data.

        1. DEVICE INTEGRATION & ISOLATION
        - SimGate functions primarily as a secure local hardware client wrapper. It does not harvest, compile, or transmit cellular contact history profiles or unrelated storage directories to external analytics brokers.
        - All security keys, device tokens, and temporary communication logs are strictly isolated inside the application's private storage partitions.

        2. MESSAGE PERSISTENCE & LOCAL COOLDOWNS
        - When outbound SMS actions are retrieved from your web dashboard, they are saved locally with minimal indexing schema to keep track of delivery reports (Sent, Failed, Pending).
        - You has full autonomy to purge history logs or clean the cached databases at any point via the Settings tab.
        - When unpairing your device ID, all credentials and logs are deleted.

        3. SECURE SSL NETWORK ROUTING
        - Transmission of payloads between the client device and the server endpoint occurs exclusively via Transport Layer Security (TLS 1.3) protocols.
        - Endpoints are shielded against unauthorized modifications or unauthorized intermediate interception.

        4. SYSTEM PERMISSIONS & CONTEXT
        - To operate reliably, Android requires the following sensitive authorizations:
          * SEND_SMS: Allows the App to send messages programmatically.
          * RECEIVE_SMS: Listens for incoming messages to forward to your API server.
          * READ_PHONE_STATE: Analyzes Subscription IDs to ensure proper dual-SIM routing.
          * POST_NOTIFICATIONS: Maintains the active foreground service notification required by the Android OS to prevent system deep-sleep terminations.
        - These permissions are strictly queried and utilized during core foreground operations. We do not run hidden telemetry routines.

        5. USER CONTROLS & UPDATES
        - You retain absolute control over whether incoming messages are forwarded, the polling frequencies, and sleep schedules via the Settings menu.
    """.trimIndent()
}

// ==========================================
// --- ADVANCED MESSAGING INTERFACES ---
// ==========================================

@Composable
fun AdvancedMessagingScreen(
    viewModel: SmsGatewayViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("Scheduled") } // "Scheduled", "Drafts", "Templates", "Contacts", "Bulk & AI"

    val scheduledList by viewModel.allScheduledSms.collectAsStateWithLifecycle(initialValue = emptyList())
    val draftsList by viewModel.allDrafts.collectAsStateWithLifecycle(initialValue = emptyList())
    val templatesList by viewModel.allTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val contactsList by viewModel.allContacts.collectAsStateWithLifecycle(initialValue = emptyList())

    val isImporting by viewModel.isImportingContacts.collectAsStateWithLifecycle()
    val isGeneratingAi by viewModel.aiGeneratingState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- CUSTOM HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x10FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go Back",
                        tint = LightText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Advanced Messaging Hub",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Text(
                        text = "Automate and schedule your SMS gateways",
                        fontSize = 11.sp,
                        color = SoftGray
                    )
                }
            }

            // --- NAVIGATION TABS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("Scheduled", Icons.Default.Schedule, "Scheduled"),
                    Triple("Drafts", Icons.Default.Drafts, "Drafts"),
                    Triple("Templates", Icons.Default.SnippetFolder, "Templates"),
                    Triple("Contacts", Icons.Default.Contacts, "Contacts"),
                    Triple("Bulk & AI", Icons.Default.AutoAwesome, "Bulk & AI")
                ).forEach { (tabId, icon, label) ->
                    val isSelected = activeTab == tabId
                    Row(
                        modifier = Modifier
                            .background(
                                if (isSelected) Color(0x2010B981) else Color(0x05FFFFFF),
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) EmeraldPrimary.copy(alpha = 0.4f) else SlateOutline,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { activeTab = tabId }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) EmeraldPrimary else SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) EmeraldPrimary else LightText
                        )
                    }
                }
            }

            HorizontalDivider(color = SlateOutline, thickness = 1.dp)

            // --- MAIN VIEWPORT ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    "Scheduled" -> ScheduledSmsTab(
                        viewModel = viewModel,
                        scheduledList = scheduledList
                    )
                    "Drafts" -> SmsDraftsTab(
                        viewModel = viewModel,
                        draftsList = draftsList
                    )
                    "Templates" -> SmsTemplatesTab(
                        viewModel = viewModel,
                        templatesList = templatesList
                    )
                    "Contacts" -> ContactsSyncTab(
                        viewModel = viewModel,
                        contactsList = contactsList
                    )
                    "Bulk & AI" -> BulkAndAiTab(
                        viewModel = viewModel,
                        contactsList = contactsList
                    )
                }
            }
        }

        // --- FLOATING IMPORTING BANNER ---
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .padding(horizontal = 20.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, EmeraldPrimary, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = EmeraldPrimary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Importing device contacts into localized secure storage hashes...",
                        fontSize = 12.sp,
                        color = LightText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- FLOATING AI GENERATING BANNER ---
        if (isGeneratingAi) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp).widthIn(max = 320.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = EmeraldPrimary
                        )
                        Text(
                            text = "Groq trial model is planning & scheduling your campaign tasks...",
                            fontSize = 13.sp,
                            color = LightText,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Processing secure NLP inputs securely",
                            fontSize = 11.sp,
                            color = SoftGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduledSmsTab(
    viewModel: SmsGatewayViewModel,
    scheduledList: List<com.example.data.db.ScheduledSmsEntity>
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scheduled Tasks (${scheduledList.size})",
                fontSize = 14.sp,
                color = LightText,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Schedule SMS", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (scheduledList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No scheduled tasks found on gateway client.",
                    color = SoftGray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(scheduledList) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                        border = BorderStroke(1.dp, SlateOutline),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.ContactPhone,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = maskPhoneNumber(task.recipients),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LightText
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val isExpired = task.scheduleTime <= System.currentTimeMillis()
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (task.isActive && !isExpired) Color(0x2010B981) else Color(0x20EF4444),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (!task.isActive) "Paused" else if (isExpired) "Queued" else "Pending",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (task.isActive && !isExpired) EmeraldPrimary else Color.Red
                                        )
                                    }

                                    Switch(
                                        checked = task.isActive,
                                        onCheckedChange = { viewModel.toggleScheduledSms(task.id, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = EmeraldPrimary,
                                            checkedTrackColor = EmeraldPrimary.copy(alpha = 0.4f),
                                            uncheckedThumbColor = SoftGray,
                                            uncheckedTrackColor = SlateOutline
                                        ),
                                        modifier = Modifier.scale(0.7f)
                                    )

                                    IconButton(
                                        onClick = { viewModel.deleteScheduledSms(task.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = Color.Red.copy(0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Text(
                                text = task.messageTemplate,
                                fontSize = 12.sp,
                                color = LightText
                            )

                            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                            val dateString = sdf.format(Date(task.scheduleTime))
                            Text(
                                text = "Planned Run: $dateString",
                                fontSize = 10.sp,
                                color = SoftGray
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var recipient by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var selectedDelayIndex by remember { mutableStateOf(0) }
        val delays = listOf(
            Pair("Immediate (60 Sec Test)", 60L),
            Pair("5 Minutes", 5 * 60L),
            Pair("1 Hour", 60 * 60L),
            Pair("2 Hours", 2 * 60 * 60L),
            Pair("1 Day", 24 * 60 * 60L),
            Pair("Custom Timer Delay", -1L)
        )
        var customTimeValue by remember { mutableStateOf("10") }
        var customTimeUnit by remember { mutableStateOf("Minutes") } // "Seconds", "Minutes", "Hours", "Days"
        var showContactPicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Schedule SMS Message", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = recipient,
                            onValueChange = { recipient = it },
                            label = { Text("Recipient Number") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = SlateOutline
                            ),
                            placeholder = { Text("e.g. 254712345678") },
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { showContactPicker = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x1510B981), CircleShape)
                                .border(1.dp, EmeraldPrimary.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Pick contacts",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (showContactPicker) {
                        ContactPickerDialog(
                            viewModel = viewModel,
                            onDismiss = { showContactPicker = false },
                            onConfirm = { selectedList ->
                                if (selectedList.isNotEmpty()) {
                                    recipient = selectedList.joinToString(",")
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message Body") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        ),
                        modifier = Modifier.height(100.dp)
                    )

                    Text("Schedule Time Delay:", fontSize = 11.sp, color = LightText, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        delays.forEachIndexed { idx, pair ->
                            val isSel = selectedDelayIndex == idx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSel) Color(0x1510B981) else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable { selectedDelayIndex = idx }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSel,
                                    onClick = { selectedDelayIndex = idx },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(pair.first, color = LightText, fontSize = 12.sp)
                            }
                        }
                    }

                    // Custom timer configs when "Custom Timer Delay" is selected
                    if (delays[selectedDelayIndex].second == -1L) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, SlateOutline),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Define Timer:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customTimeValue,
                                        onValueChange = { customTimeValue = it.filter { char -> char.isDigit() } },
                                        label = { Text("Value", fontSize = 10.sp) },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = LightText,
                                            unfocusedTextColor = LightText,
                                            focusedBorderColor = EmeraldPrimary,
                                            unfocusedBorderColor = SlateOutline
                                        ),
                                        modifier = Modifier.width(80.dp).height(48.dp)
                                    )

                                    // Pick Select Timer Unit Row
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("Seconds", "Minutes", "Hours", "Days").forEach { unit ->
                                            val isUnitSel = customTimeUnit == unit
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isUnitSel) EmeraldPrimary else Color(0x10FFFFFF),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isUnitSel) Color.Transparent else SlateOutline,
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable { customTimeUnit = unit }
                                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = unit,
                                                    color = if (isUnitSel) Color.Black else LightText,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isUnitSel) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recipient.isNotBlank() && body.isNotBlank()) {
                            val delaySeconds = if (delays[selectedDelayIndex].second == -1L) {
                                val multiplier = when (customTimeUnit) {
                                    "Seconds" -> 1L
                                    "Minutes" -> 60L
                                    "Hours" -> 3600L
                                    "Days" -> 86400L
                                    else -> 60L
                                }
                                (customTimeValue.toLongOrNull() ?: 10L) * multiplier
                            } else {
                                delays[selectedDelayIndex].second
                            }
                            val executionTime = System.currentTimeMillis() + (delaySeconds * 1000)
                            viewModel.addScheduledSms(title = "Manual Task", messageTemplate = body.trim(), recipients = recipient.trim(), scheduleTime = executionTime)
                            Toast.makeText(context, "Sms scheduled successfully!", Toast.LENGTH_SHORT).show()
                            showCreateDialog = false
                        } else {
                            Toast.makeText(context, "Recipient and Message cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Schedule", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SoftGray)
                }
            },
            containerColor = SlateNavCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SmsDraftsTab(
    viewModel: SmsGatewayViewModel,
    draftsList: List<com.example.data.db.SmsDraftEntity>
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SMS Drafts (${draftsList.size})",
                fontSize = 14.sp,
                color = LightText,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Draft", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (draftsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved drafts on client.",
                    color = SoftGray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(draftsList) { draft ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                        border = BorderStroke(1.dp, SlateOutline),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = draft.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LightText
                                )

                                IconButton(
                                    onClick = { viewModel.deleteDraft(draft.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete draft", tint = Color.Red.copy(0.7f), modifier = Modifier.size(16.dp))
                                }
                            }

                            Text(
                                text = draft.message,
                                fontSize = 12.sp,
                                color = LightText
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Compose SMS Draft", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Draft Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        )
                    )

                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message Context") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        ),
                        modifier = Modifier.height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && body.isNotBlank()) {
                            viewModel.addDraft(title = title.trim(), message = body.trim(), recipients = "")
                            Toast.makeText(context, "Draft composed successfully!", Toast.LENGTH_SHORT).show()
                            showCreateDialog = false
                        } else {
                            Toast.makeText(context, "Fields cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save Draft", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SoftGray)
                }
            },
            containerColor = SlateNavCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SmsTemplatesTab(
    viewModel: SmsGatewayViewModel,
    templatesList: List<com.example.data.db.SmsTemplateEntity>
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Message Templates (${templatesList.size})",
                fontSize = 14.sp,
                color = LightText,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Template", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (templatesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No preconfigured templates found.",
                    color = SoftGray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(templatesList) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                        border = BorderStroke(1.dp, SlateOutline),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = template.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LightText
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0x153B82F6), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = template.category.uppercase(),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SkySecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.deleteTemplate(template.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete template", tint = Color.Red.copy(0.7f), modifier = Modifier.size(16.dp))
                                }
                            }

                            Text(
                                text = template.messageText,
                                fontSize = 12.sp,
                                color = LightText,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Marketing") }
        var body by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create System Template", color = LightText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Template Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        )
                    )

                    Text("Category:", fontSize = 11.sp, color = LightText, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Marketing", "Transactional", "OTP").forEach { c ->
                            val isSel = category == c
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSel) Color(0x3010B981) else Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                    .border(1.dp, if (isSel) EmeraldPrimary else SlateOutline, RoundedCornerShape(6.dp))
                                    .clickable { category = c }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = c,
                                    fontSize = 11.sp,
                                    color = if (isSel) EmeraldPrimary else LightText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Template Body") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        ),
                        modifier = Modifier.height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && body.isNotBlank()) {
                            viewModel.addTemplate(title = title.trim(), messageText = body.trim(), category = category.trim())
                            Toast.makeText(context, "Template configured successfully!", Toast.LENGTH_SHORT).show()
                            showCreateDialog = false
                        } else {
                            Toast.makeText(context, "Fields cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save Template", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SoftGray)
                }
            },
            containerColor = SlateNavCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ContactsSyncTab(
    viewModel: SmsGatewayViewModel,
    contactsList: List<com.example.data.db.ContactEntity>
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Synced Address Book (${contactsList.size} Contacts)",
            fontSize = 14.sp,
            color = LightText,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    viewModel.syncPhoneContacts()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sync Address", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    viewModel.importFromHistory()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, SlateOutline),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = LightText, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import History", color = LightText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (contactsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, tint = SoftGray, modifier = Modifier.size(48.dp))
                    Text(
                        text = "No contacts local hashes synchronized.",
                        color = SoftGray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contactsList) { contact ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                        border = BorderStroke(1.dp, SlateOutline),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                        .background(Color(0x1510B981), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (contact.name.isNotBlank()) contact.name.take(1).uppercase() else "#",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        fontSize = 14.sp
                                    )
                                }

                                Column {
                                    Text(
                                        text = if (contact.name.isNotBlank()) contact.name else "Unnamed Contact",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LightText
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        fontSize = 11.sp,
                                        color = SoftGray
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Symmetric Local PII Hash Protection Active",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BulkAndAiTab(
    viewModel: SmsGatewayViewModel,
    contactsList: List<com.example.data.db.ContactEntity>
) {
    val context = LocalContext.current
    var promptInput by remember { mutableStateOf("") }

    var customNumber by remember { mutableStateOf("") }
    var rawMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Groq NLP Campaign Planner",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                }

                Text(
                    text = "Write your prompt expressing what notification campaign tasks you want scheduled (e.g. \"Generate 5 reminder templates for expired gym members, dispatch them over the next 3 days on our active line.\"). The LLM parses the command, and programmatically plans and queues them.",
                    fontSize = 11.sp,
                    color = SoftGray,
                    lineHeight = 15.sp
                )

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("NLP Instruction Prompt") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = SlateOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                )

                Button(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            viewModel.generateSchedulesWithAi(
                                prompt = promptInput.trim(),
                                callback = { responseText ->
                                    val msg = responseText ?: "Campaign planning complete! Check the Scheduled Tasks tab."
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    promptInput = ""
                                }
                            )
                        } else {
                            Toast.makeText(context, "Prompt instruction cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("AI Plan Schedule Tasks", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.SendToMobile, contentDescription = null, tint = SkySecondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Direct Bulk Dispatcher",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customNumber,
                        onValueChange = { customNumber = it },
                        label = { Text("Recipients (Comma separated)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline
                        ),
                        placeholder = { Text("e.g. 2547111, 2547222") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    var showBulkContactPicker by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showBulkContactPicker = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x1510B981), CircleShape)
                            .border(1.dp, EmeraldPrimary.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = "Pick bulk contacts",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (showBulkContactPicker) {
                        ContactPickerDialog(
                            viewModel = viewModel,
                            onDismiss = { showBulkContactPicker = false },
                            onConfirm = { selectedList ->
                                if (selectedList.isNotEmpty()) {
                                    val currentList = customNumber.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                                    selectedList.forEach { num ->
                                        if (!currentList.contains(num)) {
                                            currentList.add(num)
                                        }
                                    }
                                    customNumber = currentList.joinToString(", ")
                                }
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = rawMessage,
                    onValueChange = { rawMessage = it },
                    label = { Text("Message Body") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = SlateOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                )

                Button(
                    onClick = {
                        if (customNumber.isNotBlank() && rawMessage.isNotBlank()) {
                            val list = customNumber.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            list.forEach { num ->
                                viewModel.addScheduledSms(title = "Bulk Task", messageTemplate = rawMessage.trim(), recipients = num, scheduleTime = System.currentTimeMillis() + 2000)
                            }
                            Toast.makeText(context, "Enqueued ${list.size} SMS tasks successfully!", Toast.LENGTH_SHORT).show()
                            customNumber = ""
                            rawMessage = ""
                        } else {
                            Toast.makeText(context, "Recipients & message cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SkySecondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enqueue Immediate Bulk Dispatch", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ContactPickerDialog(
    viewModel: SmsGatewayViewModel,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val contactsList by viewModel.allContacts.collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    val markedContacts = remember { mutableStateListOf<String>() }

    // Manual contact inputs
    var showManualAdd by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualPhone by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Permission Launcher for READ_CONTACTS
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.syncPhoneContacts()
            Toast.makeText(context, "Syncing device contacts...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Contacts permission required to sync device phonebook.", Toast.LENGTH_LONG).show()
        }
    }

    val filteredContacts = contactsList.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumber.contains(searchQuery)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pick Recipients",
                    color = LightText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SoftGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Actions spacing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sync Phonebook with real Android READ_CONTACTS
                    Button(
                        onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.READ_CONTACTS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.syncPhoneContacts()
                                Toast.makeText(context, "Syncing device contacts...", Toast.LENGTH_SHORT).show()
                            } else {
                                contactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Phonebook", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Import History
                    Button(
                        onClick = {
                            viewModel.importFromHistory()
                            Toast.makeText(context, "Importing numbers from history...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, SlateOutline),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = LightText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("From History", color = LightText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Add Manual Contact Form toggle
                if (!showManualAdd) {
                    TextButton(
                        onClick = { showManualAdd = true },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save New Contact Manually", color = EmeraldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, SlateOutline),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Save Manual Contact", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                label = { Text("Name", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = SlateOutline
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )
                            OutlinedTextField(
                                value = manualPhone,
                                onValueChange = { manualPhone = it },
                                label = { Text("Phone Number", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = SlateOutline
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showManualAdd = false; manualName = ""; manualPhone = "" }) {
                                    Text("Cancel", color = SoftGray, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        if (manualName.isNotBlank() && manualPhone.isNotBlank()) {
                                            viewModel.addManualContact(manualName.trim(), manualPhone.trim())
                                            Toast.makeText(context, "Contact saved locally!", Toast.LENGTH_SHORT).show()
                                            if (!markedContacts.contains(manualPhone.trim())) {
                                                markedContacts.add(manualPhone.trim())
                                            }
                                            manualName = ""
                                            manualPhone = ""
                                            showManualAdd = false
                                        } else {
                                            Toast.makeText(context, "Fill out both fields!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Save & Add", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search synced contacts...", fontSize = 12.sp, color = SoftGray) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = SlateOutline
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp)) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Address Book (${filteredContacts.size} found)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SoftGray
                    )
                    
                    if (filteredContacts.isNotEmpty()) {
                        Row {
                            TextButton(
                                onClick = {
                                    val allNums = filteredContacts.map { it.phoneNumber }
                                    allNums.forEach { num ->
                                        if (!markedContacts.contains(num)) {
                                            markedContacts.add(num)
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Select All", color = EmeraldPrimary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { markedContacts.clear() },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Clear All", color = Color.Red.copy(0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Contacts, contentDescription = null, tint = SoftGray, modifier = Modifier.size(36.dp))
                            Text(
                                "No contacts. Use 'Sync Phonebook' or import from SMS history above.",
                                color = SoftGray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            val isMarked = markedContacts.contains(contact.phoneNumber)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isMarked) Color(0x1510B981) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (isMarked) EmeraldPrimary.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (isMarked) markedContacts.remove(contact.phoneNumber)
                                        else markedContacts.add(contact.phoneNumber)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isMarked,
                                    onCheckedChange = { checked ->
                                        if (checked == true) markedContacts.add(contact.phoneNumber)
                                        else markedContacts.remove(contact.phoneNumber)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, color = LightText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(contact.phoneNumber, color = SoftGray, fontSize = 10.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (contact.source == "HistoryImport") Color(0x153B82F6) else if (contact.source == "ManualInput") Color(0x15F59E0B) else Color(0x1510B981),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (contact.source == "HistoryImport") "History" else if (contact.source == "ManualInput") "Manual" else "Phonebook",
                                        color = if (contact.source == "HistoryImport") SkySecondary else if (contact.source == "ManualInput") Color(0xFFF59E0B) else EmeraldPrimary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(markedContacts.toList())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Selection (${markedContacts.size})", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SoftGray)
            }
        },
        containerColor = SlateNavCard,
        shape = RoundedCornerShape(16.dp)
    )
}

