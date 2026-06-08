package com.example.util

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object AesEncryptionHelper {
    // Encrypts a string using 16-character AES key (AES-128 ECB PKCS5Padding)
    fun encrypt(plainText: String, keyStr: String): String {
        return try {
            val keyBytes = keyStr.toByteArray(Charsets.UTF_8).copyOf(16)
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            plainText
        }
    }

    // Decrypts a base64 string using 16-character AES key
    fun decrypt(encryptedBase64: String, keyStr: String): String {
        return try {
            val keyBytes = keyStr.toByteArray(Charsets.UTF_8).copyOf(16)
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/DECRYPT_MODE") // Support normal decryption
            val realCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            realCipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
            val decodedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val decryptedBytes = realCipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            encryptedBase64
        }
    }
}

object SmsGatewaySecurityAndSyncUtils {

    fun hashSensitiveDataInPayload(payloadJsonStr: String): String {
        try {
            val root = JSONObject(payloadJsonStr)
            val sensitiveKeys = listOf(
                "phone", "phone_number", "phoneNumber", "mobile", 
                "secret", "password", "sec_token", "secret_token", "token", "key"
            )
            
            fun processObject(obj: JSONObject) {
                val keys = obj.keys().asSequence().toList()
                val keysToHash = mutableListOf<String>()
                for (key in keys) {
                    if (sensitiveKeys.any { key.equals(it, ignoreCase = true) }) {
                        keysToHash.add(key)
                    } else {
                        val value = obj.get(key)
                        if (value is JSONObject) {
                            processObject(value)
                        } else if (value is JSONArray) {
                            for (i in 0 until value.length()) {
                                val item = value.get(i)
                                if (item is JSONObject) {
                                    processObject(item)
                                }
                            }
                        }
                    }
                }
                
                // Perform SHA-256 hashing for identified sensitive keys
                keysToHash.forEach { key ->
                    val rawVal = obj.optString(key, "")
                    if (rawVal.isNotBlank() && !rawVal.endsWith(" (SHA256)")) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        val hashBytes = digest.digest(rawVal.toByteArray(Charsets.UTF_8))
                        val hexStr = hashBytes.joinToString("") { "%02x".format(it) }
                        obj.put(key, "$hexStr (SHA256)")
                    }
                }
            }
            
            processObject(root)
            return root.toString()
        } catch (e: Exception) {
            return payloadJsonStr
        }
    }

    fun downloadAndLocalizeImagesInPayload(
        context: Context, 
        payloadStr: String, 
        repository: com.example.data.db.SmsRepository
    ): String {
        try {
            val root = JSONObject(payloadStr)
            val imageKeys = listOf("image", "image_url", "imageUrl", "avatar", "photo", "avatar_url")
            
            fun processObj(obj: JSONObject) {
                val keys = obj.keys().asSequence().toList()
                for (key in keys) {
                    if (imageKeys.any { key.equals(it, ignoreCase = true) }) {
                        val url = obj.optString(key, "")
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            // Sync or async download
                            try {
                                val client = okhttp3.OkHttpClient()
                                val request = okhttp3.Request.Builder().url(url).build()
                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val bytes = response.body?.bytes()
                                        if (bytes != null) {
                                            val cacheDir = context.cacheDir
                                            val filename = "img_" + Math.abs(url.hashCode()) + ".jpg"
                                            val file = File(cacheDir, filename)
                                            file.writeBytes(bytes)
                                            obj.put("local_image_path", file.absolutePath)
                                            
                                            runBlocking {
                                                repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                                    level = "INFO",
                                                    message = "Automatically downloaded post asset image from $url. Saved securely at cache/$filename."
                                                ))
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                runBlocking {
                                    repository.insertHubLog(com.example.data.db.HubEventLogEntity(
                                        level = "WARNING",
                                        message = "Automatic asset download from $url failed: ${e.message}"
                                    ))
                                }
                            }
                        }
                    } else {
                        val value = obj.get(key)
                        if (value is JSONObject) {
                            processObj(value)
                        } else if (value is JSONArray) {
                            for (i in 0 until value.length()) {
                                val item = value.get(i)
                                if (item is JSONObject) {
                                    processObj(item)
                                }
                            }
                        }
                    }
                }
            }
            
            processObj(root)
            return root.toString()
        } catch (e: Exception) {
            return payloadStr
        }
    }
}
