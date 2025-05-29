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

data class ScamResult(val score: Int, val analysis: String)

class ChatGPTAnalyzer(private val apiKey: String) {
    private val TAG = "ChatGPTAnalyzer"
    private val client = OkHttpClient()

    suspend fun analyze(transcript: String): ScamResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "analyze() transcript=${transcript.take(50)}…")

        // 1) System prompt that enforces structured JSON + bullet‐point reasoning
        val systemPrompt = """
            You are FraudDetectGPT™—an expert in phone-call scam forensics.
            When given a call transcript, you MUST:
            1. Identify explicit scam indicators (e.g. requests for money, personal data, urgent threats).
            2. Spot subtle “build-up” patterns (e.g. multiple calls, grooming language, credibility establishment).
            3. Weigh both types of signals to compute a single “scam likelihood” score from 0–100.
            4. Explain your reasoning in 3–5 bullet points, calling out each major red flag or mitigation.
            5. Output ONLY valid JSON in this exact schema (no extra keys):
            
            {
              "score": <integer 0–100>,
              "analysis": [
                "<bullet‐point explanation 1>",
                "<bullet‐point explanation 2>",
                "... up to 5 points"
              ]
            }
            
            Use a LOW temperature (0–0.3) to keep outputs consistent.
            Always assume worst‐case when ambiguous: if you’re not sure, err on the side of a higher risk score.
        """.trimIndent()

        // 2) Build messages array
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", transcript))
        }
        Log.d(TAG, "analyze() messages=$messages")

        // 3) Construct payload
        val payload = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", messages)
            put("temperature", 0.2)
        }

        val body = payload.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        // 4) Call ChatGPT
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

        // 5) Parse out the JSON content
        val content = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
        Log.d(TAG, "Raw model output: $content")

        val resultJson = JSONObject(content)
        val score = resultJson.getInt("score")

        // 6) Convert the analysis array into a single string with line breaks
        val analysisArray: JSONArray = resultJson.getJSONArray("analysis")
        val analysisLines = (0 until analysisArray.length()).map { i ->
            "• " + analysisArray.getString(i)
        }
        val analysisText = analysisLines.joinToString("\n")

        Log.d(TAG, "analyze() parsed score=$score analysis=$analysisText")
        ScamResult(score, analysisText)
    }
}
