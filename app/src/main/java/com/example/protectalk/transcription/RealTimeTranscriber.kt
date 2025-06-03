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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeoutException

class RealTimeTranscriber {
    companion object {
        private const val TAG = "RealTimeTranscriber"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val CLIENT = OkHttpClient()

        // Always use the long-running endpoint for diarization or channel separation
        private const val DIARIZE_ENDPOINT =
            "https://speech.googleapis.com/v1/speech:longrunningrecognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
    }

    /**
     * Transcribes with speaker separation: uses channel-based separation for WAV/FLAC or diarization for others.
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Transcribing ${'$'}{audioFile.name} ===")

        // 1) Read & Base64-encode
        val bytes = audioFile.readBytes()
        val audioB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Log.d(TAG, "File size=${'$'}{bytes.size}, payload chars=${'$'}{audioB64.length}")

        // 2) Determine encoding and sample rate based on extension
        val ext = audioFile.extension.lowercase()
        val (encoding, sampleRate) = when (ext) {
            "wav"       -> "LINEAR16" to 16000
            "flac"      -> "FLAC"      to 16000
            "mp4", "m4a"-> "MPEG4"    to 16000
            "3gp", "amr"-> "AMR"      to 8000
            else         -> "ENCODING_UNSPECIFIED" to 16000
        }
        Log.d(TAG, "Using encoding=$encoding, sampleRate=$sampleRate")

        // 3) Build config: include channel separation for WAV/FLAC; otherwise diarization
        val config = JSONObject().apply {
            put("encoding", encoding)
            put("sampleRateHertz", sampleRate)
            put("languageCode", "en-US")
            put("useEnhanced", true)
            if (ext in listOf("wav", "flac")) {
                put("audioChannelCount", 2)
                put("enableSeparateRecognitionPerChannel", true)
            } else {
                put("diarizationConfig", JSONObject().apply {
                    put("enableSpeakerDiarization", true)
                    put("minSpeakerCount", 1)
                    put("maxSpeakerCount", 2)
                })
            }
        }

        // 4) Construct payload
        val payload = JSONObject().apply {
            put("config", config)
            put("audio", JSONObject().put("content", audioB64))
        }.toString()
        Log.d(TAG, "Payload JSON length=${'$'}{payload.length}")

        // 5) Kick off long-running recognition
        val operationName = CLIENT.newCall(
            Request.Builder()
                .url(DIARIZE_ENDPOINT)
                .post(payload.toRequestBody(JSON))
                .build()
        ).execute().use { resp ->
            val body = resp.body!!.string()
            Log.d(TAG, "Op start HTTP ${'$'}{resp.code}: $body")
            if (!resp.isSuccessful) throw IllegalStateException("STT error ${'$'}{resp.code}: $body")
            JSONObject(body).getString("name")
        }

        // 6) Poll until done
        val statusUrl =
            "https://speech.googleapis.com/v1/operations/${'$'}operationName?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
        repeat(60) { attempt ->
            delay(1_000L)
            CLIENT.newCall(Request.Builder().url(statusUrl).build()).execute().use { r ->
                val b = r.body!!.string()
                Log.d(TAG, "Poll #${'$'}{attempt + 1} HTTP ${'$'}{r.code}")
                if (r.isSuccessful) {
                    val json = JSONObject(b)
                    if (json.optBoolean("done", false)) {
                        // 7) Parse transcript per channel or diarization
                        val response = json.optJSONObject("response")
                        // Channel-separated results
                        response?.optJSONArray("channelTag")
                        // Diarization fallback
                        val results = response?.optJSONArray("results") ?: return@withContext ""
                        // For simplicity, combine either separate transcripts or diarized words
                        val output = StringBuilder()
                        // Channel transcripts
                        response.optJSONArray("channelTag")?.let { channels ->
                            for (i in 0 until channels.length()) {
                                val ch = channels.getJSONObject(i)
                                val chId = ch.optInt("channelTag")
                                val text = ch.optString("alternatives")
                                output.append("Channel $chId: $text\n")
                            }
                        } ?: run {
                            // Diarized words grouping
                            val words = mutableListOf<JSONObject>()
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
                            val bySpeaker = words.groupBy(
                                { it.optInt("speakerTag", 0) },
                                { it.optString("word") }
                            )
                            bySpeaker.toSortedMap().forEach { (tag, tokens) ->
                                output.append("Speaker $tag: ${'$'}{tokens.joinToString(",")}\n")
                            }
                        }
                        val transcript = output.toString().trimEnd()
                        Log.d(TAG, "Final transcript:\n$transcript")
                        return@withContext transcript
                    }
                }
            }
        }
        throw TimeoutException("Transcription timed out")
    }
}
