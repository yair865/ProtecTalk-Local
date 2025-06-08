package com.example.protectalk.transcription

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.protectalk.BuildConfig
import com.example.protectalk.utils.AudioConverter
import com.example.protectalk.utils.RecordingFinder
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

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class RealTimeTranscriber {
    companion object {
        private const val TAG = "RealTimeTranscriber"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val CLIENT = OkHttpClient()

        private const val DIARIZE_ENDPOINT =
            "https://speech.googleapis.com/v1p1beta1/speech:longrunningrecognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun transcribeFromLatestFile(): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Locating latest call recording file ===")
        val originalFile: File? = RecordingFinder.findLatestRecording()

        if (originalFile == null) {
            Log.w(TAG, "No recording file found in folders")
            return@withContext "No recording found."
        }

        Log.d(TAG, "Found recording: ${originalFile.absolutePath}")

        val fileToUse = when (originalFile.extension.lowercase()) {
            "m4a" -> {
                val wavOutput =
                    File(originalFile.parent, "${originalFile.nameWithoutExtension}.wav")
                val success = AudioConverter.convertM4aToWav(originalFile, wavOutput)
                if (!success) return@withContext "WAV conversion failed ❗"
                wavOutput
            }

            else -> originalFile
        }

        val bytes = fileToUse.readBytes()
        val audioB64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        val ext = fileToUse.extension.lowercase()
        val encoding = when (ext) {
            "wav" -> "LINEAR16"
            "flac" -> "FLAC"
            "mp4", "m4a", "3gp" -> "MPEG4"
            "amr" -> "AMR"
            else -> "ENCODING_UNSPECIFIED"
        }

        val configJson = JSONObject().apply {
            put("encoding", encoding)
            put("sampleRateHertz", 48000) // If you convert to 16kHz mono, change to 16000!
            put("languageCode", "en-US")
            put("enableSpeakerDiarization", true)
            put("diarizationSpeakerCount", 2)
            put("useEnhanced", true)
            put("model", "phone_call")
            put("enableWordTimeOffsets", true)
        }

        val payload = JSONObject().apply {
            put("config", configJson)
            put("audio", JSONObject().put("content", audioB64))
        }.toString()

        Log.d(TAG, "Uploading to STT (encoding=$encoding)")

        val operationName = CLIENT.newCall(
            Request.Builder()
                .url(DIARIZE_ENDPOINT)
                .post(payload.toRequestBody(JSON))
                .build()
        ).execute().use { resp ->
            val body = resp.body!!.string()
            if (!resp.isSuccessful) {
                Log.e(TAG, "STT start failed: ${resp.code} / $body")
                throw IllegalStateException("STT start error ${resp.code}")
            }
            JSONObject(body).getString("name")
        }

        val statusUrl =
            "https://speech.googleapis.com/v1/operations/$operationName?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"

        val MAX_ATTEMPTS = 120
        val SLEEP_MS = 1500L

        repeat(MAX_ATTEMPTS) {
            delay(SLEEP_MS)
            CLIENT.newCall(Request.Builder().url(statusUrl).build()).execute().use { r ->
                val b = r.body!!.string()
                if (r.isSuccessful) {
                    val json = JSONObject(b)
                    Log.d(TAG, "Poll response: $json")
                    if (json.optBoolean("done", false)) {
                        val response = json.optJSONObject("response") ?: return@use
                        val results = response.optJSONArray("results") ?: return@use

                        // ---- FIXED diarizedWords extraction ----
                        val diarizedWords = (0 until results.length())
                            .asSequence()
                            .flatMap { i ->
                                val alternatives = results.getJSONObject(i).getJSONArray("alternatives")
                                (0 until alternatives.length()).asSequence().flatMap { j ->
                                    val wordsArray = alternatives.getJSONObject(j).optJSONArray("words")
                                    wordsArray?.let { arr ->
                                        (0 until arr.length()).asSequence().map { k -> arr.getJSONObject(k) }
                                    } ?: emptySequence()
                                }
                            }
                            .filter { it.has("speakerTag") }
                            .toList()
                        // ---------------------------------------

                        if (diarizedWords.isEmpty()) {
                            Log.e(TAG, "No diarized words found! (Diarization missing)")
                            return@withContext "No diarized words found (diarization failed)."
                        }

                        val diarizedTranscript = buildString {
                            var lastSpeaker = -1
                            for (wordObj in diarizedWords) {
                                val speaker = wordObj.getInt("speakerTag")
                                val word = wordObj.getString("word")
                                wordObj.optString("startTime", "")

                                if (speaker != lastSpeaker) {
                                    if (lastSpeaker != -1) append("\n")
                                    append(
                                        if (speaker == 1) "caller: " else "phone user: "
                                    )
                                    lastSpeaker = speaker
                                }
                                append("$word ")
                            }
                        }.trim()

                        Log.d(TAG, "Final transcript:\n$diarizedTranscript")
                        return@withContext diarizedTranscript
                    }
                } else {
                    Log.e(TAG, "Polling error ${r.code}: ${r.body?.string()}")
                }
            }
        }

        throw TimeoutException("Transcription timed out")
    }
}
