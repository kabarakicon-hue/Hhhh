package com.example.data.api

import android.content.Context
import com.example.data.pref.AuthManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ----------------- API Models -----------------

@JsonClass(generateAdapter = true)
data class ConnectRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String
)

@JsonClass(generateAdapter = true)
data class SimpleResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "battery") val battery: Int,
    @Json(name = "signal") val signal: Int
)

@JsonClass(generateAdapter = true)
data class PollRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String
)

@JsonClass(generateAdapter = true)
data class SmsJob(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "type") val type: String, // e.g. "send_sms"
    @Json(name = "recipient") val recipient: String,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class PollResponse(
    @Json(name = "jobs") val jobs: List<SmsJob>? = null
)

@JsonClass(generateAdapter = true)
data class ReportSentRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "message_id") val messageId: String
)

@JsonClass(generateAdapter = true)
data class ReportFailedRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "message_id") val messageId: String,
    @Json(name = "reason") val reason: String
)

@JsonClass(generateAdapter = true)
data class IncomingSmsRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "sender") val sender: String,
    @Json(name = "message") val message: String
)

// ----------------- Retrofit Service -----------------

interface SmsApiService {

    @POST("device-connect")
    suspend fun connectDevice(@Body request: ConnectRequest): Response<SimpleResponse>

    @POST("device-heartbeat")
    suspend fun sendHeartbeat(@Body request: HeartbeatRequest): Response<SimpleResponse>

    @POST("device-poll")
    suspend fun pollJobs(@Body request: PollRequest): Response<PollResponse>

    @POST("sms-sent")
    suspend fun reportSent(@Body request: ReportSentRequest): Response<SimpleResponse>

    @POST("sms-failed")
    suspend fun reportFailed(@Body request: ReportFailedRequest): Response<SimpleResponse>

    @POST("incoming-sms")
    suspend fun forwardIncoming(@Body request: IncomingSmsRequest): Response<SimpleResponse>
}

// ----------------- Retrofit Builder Client -----------------

object RetrofitClient {
    private var cachedUrl: String? = null
    private var cachedService: SmsApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    fun getService(context: Context): SmsApiService {
        val authManager = AuthManager(context)
        val rawUrl = authManager.getApiUrl().trim()
        val formattedUrl = when {
            rawUrl.isBlank() -> "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/"
            rawUrl.endsWith("/") -> rawUrl
            else -> "$rawUrl/"
        }

        if (cachedService == null || cachedUrl != formattedUrl) {
            cachedUrl = formattedUrl
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(formattedUrl)
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                cachedService = retrofit.create(SmsApiService::class.java)
            } catch (e: Exception) {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                cachedService = retrofit.create(SmsApiService::class.java)
            }
        }
        return cachedService!!
    }

    val service: SmsApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(SmsApiService::class.java)
    }
}
