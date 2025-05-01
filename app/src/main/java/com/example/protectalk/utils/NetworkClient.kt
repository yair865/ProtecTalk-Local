package com.example.protectalk.utils

import android.util.Log
import com.example.protectalk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val HF_URL =
        "https://api-inference.huggingface.co/models/valhalla/distilbart-mnli-12-1?wait_for_model=true"

    private val HF_API_KEY = BuildConfig.HF_API_KEY
    private const val TAG = "NetworkClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Returns a risk score [0.0, 1.0].
     */
    suspend fun getRiskScore(segment: String): Double = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("inputs", segment)
                put("parameters", JSONObject().apply {
                    put("candidate_labels", JSONArray(listOf("scam", "not scam")))
                })
            }.toString()

            val body = payload.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(HF_URL)
                .addHeader("Authorization", "Bearer $HF_API_KEY")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "HF error [${resp.code}]: $respBody")
                    return@withContext 0.0
                }

                val json = JSONObject(respBody)
                val labels = json.getJSONArray("labels")
                val scores = json.getJSONArray("scores")

                for (i in 0 until labels.length()) {
                    if (labels.getString(i).equals("scam", ignoreCase = true)) {
                        return@withContext scores.getDouble(i)
                    }
                }

                return@withContext 0.0 // "scam" label not found
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.localizedMessage}", e)
            return@withContext 0.0 // Safe fallback on failure
        }
    }
}
