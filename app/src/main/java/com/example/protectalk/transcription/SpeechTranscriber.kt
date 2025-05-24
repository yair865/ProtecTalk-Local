package com.example.protectalk.transcription

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File

class SpeechTranscriber(private val apiKey: String) {
    private val client = OkHttpClient()

    suspend fun transcribe(file: File): String {
        val audioBytes = file.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val encoding = when (file.extension.lowercase()) {
            "wav" -> "LINEAR16"
            "mp4", "m4a" -> "MPEG4"
            "3gp", "amr" -> "AMR"
            else -> "ENCODING_UNSPECIFIED"
        }
        val requestJson = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", encoding)
                put("sampleRateHertz", 16000)
                put("languageCode", "en-US")
            })
            put("audio", JSONObject().put("content", base64Audio))
        }
        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            requestJson.toString()
        )
        val request = Request.Builder()
            .url("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body!!.string())
            val results = json.optJSONArray("results") ?: return ""
            return (0 until results.length()).joinToString(" ") { i ->
                results.getJSONObject(i)
                    .getJSONArray("alternatives")
                    .getJSONObject(0)
                    .getString("transcript")
            }
        }
    }
}