package com.example.protectalk.transcription

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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeoutException

class RealTimeTranscriber {
    companion object {
        private const val TAG = "RealTimeTranscriber"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val CLIENT = OkHttpClient()

        // Always use the long-running endpoint for diarization
        private const val DIARIZE_ENDPOINT =
            "https://speech.googleapis.com/v1/speech:longrunningrecognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
    }

    /**
     * Transcribes with speaker diarization (2 speakers).
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Diarization transcribe ${audioFile.name} ===")

        // 1) Read & Base64-encode
        val bytes = audioFile.readBytes()
        val audioB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Log.d(TAG, "File size=${bytes.size}, payload chars=${audioB64.length}")

        // 2) Build config with diarizationConfig
        val config = JSONObject().apply {
            put("encoding", when (audioFile.extension.lowercase()) {
                "wav"       -> "LINEAR16"
                "mp4","m4a" -> "MPEG4"
                "3gp","amr" -> "AMR"
                else        -> "ENCODING_UNSPECIFIED"
            })
            // use correct sample rates
            put("sampleRateHertz",
                if (audioFile.extension.lowercase() in setOf("3gp","amr")) 8000 else 16000
            )
            put("languageCode", "en-US")
            // nested diarizationConfig object
            put("diarizationConfig", JSONObject().apply {
                put("enableSpeakerDiarization", true)
                put("minSpeakerCount", 2)
                put("maxSpeakerCount", 2)
            })
        }

        // 3) Wrap into full payload
        val payload = JSONObject().apply {
            put("config", config)
            put("audio", JSONObject().put("content", audioB64))
        }.toString()
        Log.d(TAG, "Payload JSON length=${payload.length}")

        // 4) Kick off the long-running job
        val operationName = CLIENT.newCall(
            Request.Builder()
                .url(DIARIZE_ENDPOINT)
                .post(payload.toRequestBody(JSON))
                .build()
        ).execute().use { resp ->
            val body = resp.body!!.string()
            Log.d(TAG, "Op start HTTP ${resp.code}: $body")
            if (!resp.isSuccessful) {
                throw IllegalStateException("STT error ${resp.code}: $body")
            }
            JSONObject(body).getString("name")
        }

        // 5) Poll until done
        val statusUrl = "https://speech.googleapis.com/v1/operations/$operationName?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
        repeat(60) { attempt ->
            delay(1_000L)
            CLIENT.newCall(Request.Builder().url(statusUrl).build()).execute().use { r ->
                val b = r.body!!.string()
                Log.d(TAG, "Poll #${attempt+1} HTTP ${r.code}")
                if (r.isSuccessful) {
                    val json = JSONObject(b)
                    if (json.optBoolean("done", false)) {
                        // 6) Extract every word's speakerTag
                        val words = mutableListOf<JSONObject>()
                        json.optJSONObject("response")
                            ?.optJSONArray("results")
                            ?.let { results ->
                                for (i in 0 until results.length()) {
                                    val alt = results.getJSONObject(i)
                                        .getJSONArray("alternatives")
                                        .getJSONObject(0)
                                    alt.optJSONArray("words")?.let { wArr ->
                                        for (j in 0 until wArr.length()) {
                                            words.add(wArr.getJSONObject(j))
                                        }
                                    }
                                }
                            }

                        // 7) Group by speakerTag
                        val bySpeaker = words.groupBy(
                            keySelector  = { it.optInt("speakerTag", 0) },
                            valueTransform = { it.optString("word") }
                        )

                        // 8) Build labeled transcript
                        val out = StringBuilder()
                        bySpeaker.toSortedMap().forEach { (tag, tokens) ->
                            out.append("Speaker $tag: ")
                                .append(tokens.joinToString(" "))
                                .append("\n")
                        }
                        return@withContext out.toString().trimEnd()
                    }
                }
            }
        }

        throw TimeoutException("Diarization timed out")
    }
}
