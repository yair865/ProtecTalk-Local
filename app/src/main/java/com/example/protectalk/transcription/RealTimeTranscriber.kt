package com.example.protectalk.transcription

import android.media.MediaExtractor
import android.media.MediaFormat
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

        // Endpoints for sync vs. async
        private const val SYNCH_ENDPOINT =
            "https://speech.googleapis.com/v1/speech:recognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
        private const val ASYNC_ENDPOINT =
            "https://speech.googleapis.com/v1/speech:longrunningrecognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
    }

    /**
     * Transcribes the given audio file.
     * - ≤60 s: synchronous speech:recognize
     * - >60 s: asynchronous longrunningrecognize
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        // 1) Measure duration using MediaExtractor
        val durationMs = try {
            val extractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            val trackIndex = (0 until extractor.trackCount).first { idx ->
                extractor.getTrackFormat(idx)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            extractor.release()
            durationUs / 1000L
        } catch (_: Exception) {
            0L
        }

        // 2) Read & encode audio contents
        val bytes = audioFile.readBytes()
        val audioB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val config = JSONObject().apply {
            put("encoding", "AMR_NB")
            put("sampleRateHertz", 8000)
            put("languageCode", "en-US")
        }
        val audio = JSONObject().put("content", audioB64)
        val payload = JSONObject().apply {
            put("config", config)
            put("audio", audio)
        }.toString()

        // 3) Choose endpoint based on duration
        val endpoint = if (durationMs <= 60_000L) SYNCH_ENDPOINT else ASYNC_ENDPOINT
        Log.d(TAG, "Using endpoint $endpoint for ${audioFile.name} (duration=${durationMs}ms)")
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody(JSON))
            .build()

        CLIENT.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e(TAG, "STT error ${resp.code}: $body")
                throw IllegalStateException("Google STT error ${resp.code}: $body")
            }
            Log.d(TAG, "STT response: $body")

            return@withContext if (durationMs <= 60_000L) {
                // synchronous response
                JSONObject(body)
                    .optJSONArray("results")
                    ?.optJSONObject(0)
                    ?.optJSONArray("alternatives")
                    ?.optJSONObject(0)
                    ?.optString("transcript")
                    .orEmpty()
            } else {
                // asynchronous: poll operation
                val opName = JSONObject(body).optString("name")
                if (opName.isBlank()) throw IllegalStateException("No operation name returned")
                pollOperation(opName)
            }
        }
    }

    private suspend fun pollOperation(opName: String): String = withContext(Dispatchers.IO) {
        val statusUrl =
            "https://speech.googleapis.com/v1/operations/$opName?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
        repeat(60) { attempt ->
            delay(1_000L)
            CLIENT.newCall(Request.Builder().url(statusUrl).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Poll failed ${resp.code}: $body")
                    return@withContext ""
                }
                val json = JSONObject(body)
                if (json.optBoolean("done", false)) {
                    return@withContext json
                        .optJSONObject("response")
                        ?.optJSONArray("results")
                        ?.optJSONObject(0)
                        ?.optJSONArray("alternatives")
                        ?.optJSONObject(0)
                        ?.optString("transcript")
                        .orEmpty()
                }
            }
        }
        throw TimeoutException("Long-running STT timed out")
    }
}
