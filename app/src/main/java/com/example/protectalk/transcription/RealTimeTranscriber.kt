// RealTimeTranscriber.kt
package com.example.protectalk.transcription

import android.media.MediaMetadataRetriever
import android.util.Base64
import android.util.Log
import com.example.protectalk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeoutException

class RealTimeTranscriber {
    companion object {
        private const val TAG = "RealTimeTranscriber"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val CLIENT = OkHttpClient()

        private const val SYNCH_ENDPOINT =
            "https://speech.googleapis.com/v1/speech:recognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
        private const val ASYNC_ENDPOINT =
            "https://speech.googleapis.com/v1/speech:longrunningrecognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
    }

    /**
     * Transcribes the given audio file.
     * - ≤60 s → synchronous recognize()
     * - > 60 s → longrunningrecognize() + polling
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Transcribing ${audioFile.name} ===")

        // 1) Measure duration via MediaMetadataRetriever
        val durationMs = try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(audioFile.absolutePath)
            val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            mmr.release()
            val ms = durStr?.toLongOrNull() ?: 0L
            Log.d(TAG, "Measured duration: ${ms}ms")
            ms
        } catch (e: Exception) {
            Log.w(TAG, "Could not measure duration, assuming short file", e)
            0L
        }

        // 2) Read & Base64-encode file bytes
        Log.d(TAG, "Reading ${audioFile.length()} bytes from file")
        val bytes = audioFile.readBytes()
        val audioB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Log.d(TAG, "Base64 payload size: ${audioB64.length} chars")

        // 3) Build JSON payload
        val config = JSONObject().apply {
            put("encoding", "AMR")           // proper enum for .amr
            put("sampleRateHertz", 8000)
            put("languageCode", "en-US")
            put("enableSpeakerDiarization", true)
            put("diarizationSpeakerCount", 2)
        }
        val audio = JSONObject().put("content", audioB64)
        val payload = JSONObject().apply {
            put("config", config)
            put("audio", audio)
        }.toString()
        Log.d(TAG, "Payload JSON length: ${payload.length}")

        // 4) Choose endpoint and POST
        val endpoint = if (durationMs <= 60_000L) SYNCH_ENDPOINT else ASYNC_ENDPOINT
        Log.d(TAG, "Using endpoint: $endpoint")
        CLIENT.newCall(
            Request.Builder()
                .url(endpoint)
                .post(payload.toRequestBody(JSON))
                .build()
        ).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            Log.d(TAG, "HTTP ${resp.code} response: $body")
            if (!resp.isSuccessful) {
                Log.e(TAG, "STT error ${resp.code}")
                throw IllegalStateException("Google STT error ${resp.code}: $body")
            }

            return@withContext if (durationMs <= 60_000L) {
                // sync recognition
                val transcript = JSONObject(body)
                    .optJSONArray("results")
                    ?.optJSONObject(0)
                    ?.optJSONArray("alternatives")
                    ?.optJSONObject(0)
                    ?.optString("transcript")
                    .orEmpty()
                Log.d(TAG, "Sync transcript: '$transcript'")
                transcript
            } else {
                // async recognition
                val opName = JSONObject(body).optString("name")
                Log.d(TAG, "Long-running operation name: $opName")
                if (opName.isBlank()) {
                    throw IllegalStateException("No operation name returned")
                }
                pollOperation(opName)
            }
        }
    }

    private suspend fun pollOperation(opName: String): String = withContext(Dispatchers.IO) {
        val statusUrl =
            "https://speech.googleapis.com/v1/operations/$opName?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
        Log.d(TAG, "Polling operation at $statusUrl")
        repeat(60) { attempt ->
            delay(1_000L)
            Log.d(TAG, "Poll attempt #${attempt + 1}")
            CLIENT.newCall(Request.Builder().url(statusUrl).build()).execute().use { r ->
                val b = r.body?.string().orEmpty()
                Log.d(TAG, "Poll HTTP ${r.code}, body length=${b.length}")
                if (!r.isSuccessful) {
                    Log.e(TAG, "Poll failed ${r.code}")
                    return@withContext ""
                }
                val json = JSONObject(b)
                if (json.optBoolean("done", false)) {
                    val transcript = json
                        .optJSONObject("response")
                        ?.optJSONArray("results")
                        ?.optJSONObject(0)
                        ?.optJSONArray("alternatives")
                        ?.optJSONObject(0)
                        ?.optString("transcript")
                        .orEmpty()
                    Log.d(TAG, "Async transcript: '$transcript'")
                    return@withContext transcript
                }
            }
        }
        Log.e(TAG, "Long-running STT timed out")
        throw TimeoutException("Long-running STT timed out")
    }
}
