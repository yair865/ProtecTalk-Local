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

data class ScamResult(val score: Int, val scamPhase: String, val analysis: String)

class ChatGPTAnalyzer(private val apiKey: String) {
    private val TAG = "ChatGPTAnalyzer"
    private val client = OkHttpClient()

    suspend fun analyze(transcript: String): ScamResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "analyze() transcript=${transcript.take(50)}…")

        val systemPrompt = """
            You are FraudDetectGPT™, an expert fraud analyst specializing in phone-based scams, particularly the "Phantom Hacker" scam.
            
            Analyze the given transcription and provide:

            1. Scam Probability: Rate the likelihood from 0 to 100 that this conversation is part of a Phantom Hacker scam, either actively in progress or in an initial build-up phase.

            2. Scam Phase Detection: Identify explicitly if the call is in one of these phases:
               - Initial Contact
               - Escalation
               - Financial Fraud
               - None Detected

            3. Explanation

            Return your response exclusively in this JSON format:

            {
              "scam_probability": <integer 0–100>,
              "scam_phase": "<Initial Contact | Escalation | Financial Fraud | None Detected>",
              "analysis": [
                "<bullet‐point explanation 1>",
                "<bullet‐point explanation 2>",
                "... up to 5 points"
              ]
            }
            
            Use LOW temperature (0–0.3) for consistency. Err towards caution if uncertain.
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", transcript))
        }
        Log.d(TAG, "analyze() messages=$messages")

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
        Log.d(TAG, "Raw model output: $content")

        val resultJson = JSONObject(content)
        val score = resultJson.getInt("scam_probability")
        val scamPhase = resultJson.getString("scam_phase")

        val analysisArray: JSONArray = resultJson.getJSONArray("analysis")
        val analysisLines = (0 until analysisArray.length()).map { i ->
            "• " + analysisArray.getString(i)
        }
        val analysisText = analysisLines.joinToString("\n")

        Log.d(TAG, "analyze() parsed score=$score scamPhase=$scamPhase analysis=$analysisText")
        ScamResult(score, scamPhase, analysisText)
    }
}