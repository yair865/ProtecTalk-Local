package com.example.protectalk.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds the result from the ChatGPT fraud analysis.
 */
data class ScamResult(val score: Int, val analysis: String)

/**
 * Calls OpenAI's ChatGPT API to analyze a transcript for scam potential.
 */
class ChatGPTAnalyzer(private val apiKey: String) {
    private val client = OkHttpClient()

    suspend fun analyze(transcript: String): ScamResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
You are an expert fraud analyst. Given a phone call transcript, determine the likelihood (0–100) 
that this call is a scam or part of a multi-call scam build-up. Return ONLY valid JSON:
{"score":<integer>,"analysis":"<brief explanation>"}
""".trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", transcript))
        }

        val payload = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", messages)
            put("temperature", 0.2)
        }

        val body = payload.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val content = JSONObject(resp.body!!.string())
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content").trim()
            val resultJson = JSONObject(content)
            ScamResult(
                resultJson.getInt("score"),
                resultJson.getString("analysis")
            )
        }
    }
}