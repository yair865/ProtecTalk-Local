package com.example.protectalk.analysis

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ScamResult(
    val score: Int,
    val analysisPoints: List<String>
)

class ChatGPTAnalyzer(private val apiKey: String) {
    private val TAG = "ChatGPTAnalyzer"
    private val client = OkHttpClient()

    suspend fun analyze(transcript: String): ScamResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "analyze() transcript=${transcript.take(50)}...")

        val systemPrompt = """
        You are FraudDetectGPT™, specialized in identifying phone scams such as Phantom Hacker, tech-support, bank impersonation, and police impersonation.

        Carefully evaluate the given transcript:
        - Legitimate calls from real authorities (e.g., actual police, bank personnel) should NOT be falsely identified as scams.
        - Provide a "scam_score" (0–100) based on genuine scam indicators only.
        - Clearly justify your assessment with up to 3 concise bullet points.

        Respond ONLY in JSON format:

        {
          "scam_score": <0–100>,
          "analysis": [
            "<bullet-point 1>",
            "<bullet-point 2>",
            "<bullet-point 3>"
          ]
        }
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", transcript))
        }
        Log.d(TAG, "messages=$messages")
        val payload = JSONObject().apply {
            put("model", "gpt-4-turbo")
            put("messages", messages)
            put("temperature", 0.2)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val responseText = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
        ).execute().use { resp ->
            val text = resp.body!!.string()
            Log.d(TAG, "ChatGPT HTTP ${resp.code}: $text")
            text
        }

        val content = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        val resultJson = try {
            JSONObject(content)
        } catch (e: Exception) {
            Log.e(TAG, "Parsing failed: ${e.message}")
            return@withContext ScamResult(
                score = 0,
                analysisPoints = listOf("⚠️ Could not parse GPT response.", "Raw content:", content)
            )
        }

        val score = resultJson.optInt("scam_score", 0)
        val analysisArray = resultJson.optJSONArray("analysis") ?: JSONArray()
        val analysisPoints = mutableListOf<String>()
        for (i in 0 until analysisArray.length()) {
            analysisPoints.add(analysisArray.optString(i))
        }

        ScamResult(score, analysisPoints)
    }
}
