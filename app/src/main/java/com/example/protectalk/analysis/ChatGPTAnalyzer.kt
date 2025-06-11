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

/**
 * Represents the result of a scam analysis using a language model.
 */
data class ScamResult(
    val score: Int,
    val analysisPoints: List<String>
)

/**
 * Handles scam analysis logic by invoking the OpenAI GPT API with a specialized prompt.
 *
 * @param apiKey The OpenAI API key used for authentication.
 */
class ChatGPTAnalyzer(private val apiKey: String) {

    companion object {
        private const val LOG_TAG: String = "ChatGPTAnalyzer"

        private const val OPENAI_CHAT_COMPLETION_ENDPOINT: String =
            "https://api.openai.com/v1/chat/completions"

        private const val MODEL_NAME: String = "gpt-4-turbo"
        private const val TEMPERATURE: Double = 0.2

        private const val SCAM_SCORE_KEY = "scam_score"
        private const val ANALYSIS_KEY = "analysis"

        private val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()

        private const val MAX_ANALYSIS_POINTS = 3

        private val SYSTEM_PROMPT: String = """
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
    }

    private val httpClient: OkHttpClient = OkHttpClient()

    /**
     * Submits a transcript to GPT for scam analysis and returns a [ScamResult].
     *
     * @param transcript The call transcript to analyze.
     * @return The scam score and analysis bullet points.
     */
    suspend fun analyze(transcript: String): ScamResult = withContext(Dispatchers.IO) {
        Log.d(LOG_TAG, "🧠 Analyzing transcript (preview): ${transcript.take(50)}...")

        val requestMessages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            put(JSONObject().put("role", "user").put("content", transcript))
        }

        val requestPayload = JSONObject().apply {
            put("model", MODEL_NAME)
            put("messages", requestMessages)
            put("temperature", TEMPERATURE)
        }

        val httpRequest = Request.Builder()
            .url(OPENAI_CHAT_COMPLETION_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val rawResponseText: String = httpClient.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            Log.d(LOG_TAG, "🔗 GPT HTTP ${response.code}: $responseBody")
            responseBody
        }

        val messageContent: String = try {
            JSONObject(rawResponseText)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "❌ Failed to extract message from response: ${e.message}", e)
            return@withContext ScamResult(
                score = 0,
                analysisPoints = listOf(
                    "⚠️ GPT response format error.",
                    "Raw response: ${rawResponseText.take(150)}"
                )
            )
        }

        val resultJson: JSONObject = try {
            JSONObject(messageContent)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "❌ Failed to parse GPT message as JSON: ${e.message}", e)
            return@withContext ScamResult(
                score = 0,
                analysisPoints = listOf("⚠️ Could not parse GPT response.", "Raw content:", messageContent)
            )
        }

        val scamScore: Int = resultJson.optInt(SCAM_SCORE_KEY, 0)
        val analysisJsonArray: JSONArray = resultJson.optJSONArray(ANALYSIS_KEY) ?: JSONArray()

        val bulletPoints: List<String> = List(analysisJsonArray.length()) { i ->
            analysisJsonArray.optString(i)
        }.take(MAX_ANALYSIS_POINTS)

        ScamResult(score = scamScore, analysisPoints = bulletPoints)
    }
}
