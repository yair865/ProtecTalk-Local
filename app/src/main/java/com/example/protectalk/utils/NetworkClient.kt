package com.example.protectalk.utils

import android.util.Log
import com.example.protectalk.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object NetworkClient {
    // Zero-shot classification endpoint with wait_for_model to avoid cold-start 503s
    private const val HF_URL =
        "https://api-inference.huggingface.co/models/" +
                "valhalla/distilbart-mnli-12-1?wait_for_model=true"

    // Injected from local.properties via BuildConfig
    private val HF_API_KEY = BuildConfig.HF_API_KEY

    private val client = OkHttpClient()
    private const val TAG = "NetworkClient"

    /**
     * Returns a risk score [0.0,1.0] where values closer to 1.0 mean more likely scam.
     */
    fun getRiskScore(segment: String): Double {
        // 1) Build zero-shot payload
        val payload = JSONObject().apply {
            put("inputs", segment)
            put("parameters", JSONObject().apply {
                put("candidate_labels", JSONArray(listOf("scam", "not scam")))
            })
        }.toString()

        val mediaType = "application/json".toMediaType()
        val body = payload.toRequestBody(mediaType)

        // 2) Execute HTTP call
        val request = Request.Builder()
            .url(HF_URL)
            .addHeader("Authorization", "Bearer $HF_API_KEY")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e(TAG, "HF error [${resp.code}]: $respBody")
                throw RuntimeException("Hugging Face API error ${resp.code}")
            }
            Log.d(TAG, "HF raw response: $respBody")

            // 3) Parse JSON response
            val json = JSONObject(respBody)
            val labels = json.getJSONArray("labels")
            val scores = json.getJSONArray("scores")

            // 4) Find the index of "scam" and return its score
            for (i in 0 until labels.length()) {
                if (labels.getString(i).equals("scam", ignoreCase = true)) {
                    return scores.getDouble(i)
                }
            }

            // Fallback if "scam" not found
            return 0.0
        }
    }
}
