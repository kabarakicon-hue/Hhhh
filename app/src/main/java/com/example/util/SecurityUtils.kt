package com.example.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Socket
import java.security.MessageDigest
import kotlin.system.exitProcess

object SecurityUtils {
    private const val TAG = "SecurityHub"

    // Threat flags detected at runtime
    var isDebuggerDetected = false
    var isEmulatorDetected = false
    var isRootDetected = false
    var isFridaHookDetected = false
    var isTamperedDetected = false

    /**
     * Conduct deep system audit across all threat profiles.
     */
    fun performSecurityAudit(context: Context): SecurityReport {
        isDebuggerDetected = checkDebugger(context)
        isEmulatorDetected = checkEmulator()
        isRootDetected = checkRoot()
        isFridaHookDetected = checkFridaXposed()
        isTamperedDetected = checkSignatureTamper(context)

        Log.i(TAG, "Security audit completed. Debugger: $isDebuggerDetected, Emulator: $isEmulatorDetected, Root: $isRootDetected, Frida/Xposed: $isFridaHookDetected, Tamper: $isTamperedDetected")

        return SecurityReport(
            isDebuggerDetected = isDebuggerDetected,
            isEmulatorDetected = isEmulatorDetected,
            isRootDetected = isRootDetected,
            isFridaHookDetected = isFridaHookDetected,
            isTamperedDetected = isTamperedDetected
        )
    }

    /**
     * Detects if emulator environment is active.
     */
    private fun checkEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD == "QC_Reference_Phone" // standard fallback
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Inspect superuser binaries and root conditions.
     */
    private fun checkRoot(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/sbin/su",
            "/usr/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        // Check build tags
        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) {
            return true
        }
        return false
    }

    /**
     * Check if a debugger or debugger-related flags are present.
     */
    private fun checkDebugger(context: Context): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            return true
        }
        return checkTracerPid()
    }

    /**
     * Inspects /proc/self/status for non-zero TracerPid indicating attached debug tools.
     */
    private fun checkTracerPid(): Boolean {
        try {
            val file = File("/proc/self/status")
            if (file.exists()) {
                val reader = BufferedReader(FileReader(file))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line != null && line!!.startsWith("TracerPid:")) {
                        val tracerPidStr = line!!.substring("TracerPid:".length).trim()
                        val tracerPid = tracerPidStr.toIntOrNull() ?: 0
                        if (tracerPid > 0) {
                            reader.close()
                            return true
                        }
                    }
                }
                reader.close()
            }
        } catch (e: Exception) {
            // Fallback
        }
        return false
    }

    /**
     * Checks for Frida maps hooks and Xposed memory pointers.
     */
    private fun checkFridaXposed(): Boolean {
        // 1. Stack trace inspection for Xposed
        try {
            throw Exception("Security Trace")
        } catch (e: Exception) {
            for (stackElement in e.stackTrace) {
                val cls = stackElement.className
                if (cls.contains("de.robv.android.xposed") || cls.contains("org.meowcat.edxposed")) {
                    return true
                }
            }
        }

        // 2. Map file inspection for frida-agent
        try {
            val file = File("/proc/self/maps")
            if (file.exists()) {
                val reader = BufferedReader(FileReader(file))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line != null && (line!!.contains("frida") || line!!.contains("xposed"))) {
                        reader.close()
                        return true
                    }
                }
                reader.close()
            }
        } catch (e: Exception) {
            // Ignore permission issues
        }

        return false
    }

    /**
     * Check Signature Tampering using TOFU (Trust On First Use) pattern saved in shared preferences
     * to dynamically self-validate after deployment across compiling runtimes.
     */
    private fun checkSignatureTamper(context: Context): Boolean {
        try {
            val currentSign = getSignatureHash(context) ?: return false
            val sp = context.getSharedPreferences("app_security_vault", Context.MODE_PRIVATE)
            val savedSign = sp.getString("first_install_signature", "")

            if (savedSign.isNullOrEmpty()) {
                // First launch, capture and freeze signature
                sp.edit().putString("first_install_signature", currentSign).apply()
                return false
            } else {
                // Subsequent launches: match and enforce signature integrity
                if (currentSign != savedSign) {
                    Log.e(TAG, "SIGNATURE SPLICING DETECTED! Current: $currentSign != Saved: $savedSign")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error matching signature hashes", e)
        }
        return false
    }

    private fun getSignatureHash(context: Context): String? {
        return try {
            val pm = context.packageManager
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pInfo.signatures
            }

            if (signatures != null && signatures.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signatures[0].toByteArray())
                Base64.encodeToString(md.digest(), Base64.DEFAULT).trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Trigger self-defense shutdown mechanism.
     */
    fun activateDefenseEnforcer() {
        Log.e(TAG, "Security violation active. Self-defense shutdown triggered.")
        exitProcess(0)
    }
}

data class SecurityReport(
    val isDebuggerDetected: Boolean,
    val isEmulatorDetected: Boolean,
    val isRootDetected: Boolean,
    val isFridaHookDetected: Boolean,
    val isTamperedDetected: Boolean
) {
    fun hasAnyThreat(): Boolean {
        return isDebuggerDetected || isEmulatorDetected || isRootDetected || isFridaHookDetected || isTamperedDetected
    }
}
