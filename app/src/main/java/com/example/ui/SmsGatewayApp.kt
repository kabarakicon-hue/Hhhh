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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SmsGatewayApp(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("Home") } // "Home", "History", "SIMs", "Settings"

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

                when (currentTab) {
                    "Home" -> {
                        val hasCreds = viewModel.hasSavedCredentials()
                        if (hasCreds) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onViewAllHistory = { currentTab = "History" }
                            )
                        } else {
                            ConnectScreen(viewModel = viewModel)
                        }
                    }
                    "History" -> HistoryScreen(viewModel = viewModel)
                    "SIMs" -> SimsScreen(viewModel = viewModel)
                    "Settings" -> SettingsScreen(viewModel = viewModel, onDisconnect = { currentTab = "Home" })
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
    val apiUrl by viewModel.apiUrlInput.collectAsStateWithLifecycle()
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

                // Website / API URL Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Website / API URL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SoftGray
                    )
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { viewModel.apiUrlInput.value = it },
                        placeholder = { Text("e.g. https://your-server.com/api/", color = Color.Gray, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, tint = SoftGray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SlateOutline,
                            focusedContainerColor = Color(0xFF0C121A),
                            unfocusedContainerColor = Color(0xFF0C121A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_url_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

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
                val guides = listOf(
                    "1. Login to your central Supabase dashboard web app",
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
    }

    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateNavCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Supabase SMS Server", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LightText)
                    Text("Ensure your Supabase Edge Functions routing tables support SMS queues. The server receives updates of sent/failed messages and schedules outgoing jobs on a FIFO basis.", color = SoftGray, fontSize = 13.sp)
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
fun DashboardScreen(viewModel: SmsGatewayViewModel, onViewAllHistory: () -> Unit) {
    val scrollState = rememberScrollState()
    val state by viewModel.gatewayState.collectAsStateWithLifecycle()
    val recentLogs by viewModel.filteredHistoryLogs.collectAsStateWithLifecycle(emptyList())
    val activeSims by viewModel.activeSimsList.collectAsStateWithLifecycle()
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
                                Switch(
                                    checked = sim.isActive,
                                    onCheckedChange = { /* Dynamic read state helper */ },
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
                        text = log.phoneNumber,
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
fun SettingsScreen(viewModel: SmsGatewayViewModel, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.gatewayState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("System Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = LightText)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavCard),
            border = BorderStroke(1.dp, SlateOutline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Stored Credentials", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightText)

                // Device ID info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Device ID", fontSize = 11.sp, color = SoftGray)
                        Text(viewModel.deviceIdInput.value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LightText)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(viewModel.deviceIdInput.value))
                        Toast.makeText(context, "Copied Device ID!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", tint = EmeraldPrimary)
                    }
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Device Token info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Device Token", fontSize = 11.sp, color = SoftGray)
                        Text("•••••••••••••••••", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LightText)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(viewModel.deviceTokenInput.value))
                        Toast.makeText(context, "Copied Device Token!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", tint = EmeraldPrimary)
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
                Text("Gateway Controls", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LightText)

                // Restart gateway button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.forceRefreshState()
                            Toast.makeText(context, "Restarting gateway queue polling", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = SoftGray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Restart Gateway", color = LightText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Clear history logs button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.clearAllHistory()
                            Toast.makeText(context, "History logs database purged!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Clear Cache & History", color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Notification Settings row helper
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Launch notification settings intent
                            val intent = Intent().apply {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Settings intent not supported", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SoftGray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Notification settings", color = LightText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
                }

                Divider(color = SlateOutline, thickness = 0.5.dp)

                // Battery Optimization exemption button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback info if action requires whitelisting inside the central apps page
                                val fallbackIntent = Intent().apply {
                                    action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                }
                                try {
                                    context.startActivity(fallbackIntent)
                                } catch (err: Exception) {
                                    Toast.makeText(context, "Optimization Whitelist is managed by system settings", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = SoftGray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Battery optimization exemption", color = LightText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
                }
            }
        }

        // Disconnect credential reset button
        Button(
            onClick = {
                viewModel.disconnectDevice(onCompleted = onDisconnect)
                Toast.makeText(context, "Disconnected successfully!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1410)),
            border = BorderStroke(1.dp, Color.Red.copy(0.4f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.Red)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Disconnect & Remove Keys", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
