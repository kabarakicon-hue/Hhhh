package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import com.example.data.pref.AuthManager
import com.example.ui.SmsGatewayApp
import com.example.ui.SmsGatewayViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SecurityUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authManager = AuthManager(this)
        
        // Run a silent startup security audit in the background for logging purposes
        val report = SecurityUtils.performSecurityAudit(this)

        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[SmsGatewayViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val antiScreenshot = viewModel.antiScreenshotEnabled.collectAsState()
                
                LaunchedEffect(antiScreenshot.value) {
                    if (antiScreenshot.value) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                
                SmsGatewayApp(viewModel = viewModel)
            }
        }
    }
}
