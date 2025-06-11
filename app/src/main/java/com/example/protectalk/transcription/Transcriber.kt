package com.example.protectalk.transcription

import android.os.Build
import android.util.Base64
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

/**
 * Handles finding the latest audio recording and transcribing it using Google's Speech-to-Text API
 * with speaker diarization enabled.
 */
class Transcriber {

    companion object {
        // == Logging ==
        private const val TAG: String = "RealTimeTranscriber"

        // == HTTP ==
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val httpClient: OkHttpClient = OkHttpClient()

        // == Google STT ==
        private const val GOOGLE_SPEECH_TO_TEXT_ENDPOINT: String =
            "https://speech.googleapis.com/v1p1beta1/speech:longrunningrecognize?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"

        private const val MAX_POLLING_ATTEMPTS: Int = 120
        private const val POLLING_DELAY_MS: Long = 1500L
    }

    /**
     * Locates the most recent recording, converts it if necessary, and sends it to Google's
     * Speech-to-Text API for diarized transcription. Returns the final labeled transcript.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun transcribeFromLatestFile(): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Searching for latest call recording...")

        val latestRecordingFile: File? = RecordingFinder.findLatestRecording()
        if (latestRecordingFile == null) {
            Log.w(TAG, "No recent recording found.")
            return@withContext "No recording found."
        }

        Log.d(TAG, "Found recording: ${latestRecordingFile.absolutePath}")

        val inputAudioFile: File = when (latestRecordingFile.extension.lowercase()) {
            "m4a" -> {
                val wavFile = File(latestRecordingFile.parent, "${latestRecordingFile.nameWithoutExtension}.wav")
                val conversionSuccessful: Boolean = AudioConverter.convertM4aToWav(latestRecordingFile, wavFile)
                if (!conversionSuccessful) return@withContext "WAV conversion failed ❗"
                wavFile
            }

            else -> latestRecordingFile
        }

        val audioBytes: ByteArray = inputAudioFile.readBytes()
        val audioBase64: String = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val audioFormatExtension: String = inputAudioFile.extension.lowercase()
        val encodingType: String = when (audioFormatExtension) {
            "wav" -> "LINEAR16"
            "flac" -> "FLAC"
            "mp4", "m4a", "3gp" -> "MPEG4"
            "amr" -> "AMR"
            else -> "ENCODING_UNSPECIFIED"
        }

        val recognitionConfigJson: JSONObject = JSONObject().apply {
            put("encoding", encodingType)
            put("sampleRateHertz", 48000) // Note: Change to 16000 if down sampled
            put("languageCode", "en-US")
            put("enableSpeakerDiarization", true)
            put("diarizationSpeakerCount", 2)
            put("useEnhanced", true)
            put("model", "phone_call")
            put("enableWordTimeOffsets", true)
        }

        val sttPayload: String = JSONObject().apply {
            put("config", recognitionConfigJson)
            put("audio", JSONObject().put("content", audioBase64))
        }.toString()

        Log.d(TAG, "Uploading audio to Google STT (encoding=$encodingType)")

        val operationId: String = httpClient.newCall(
            Request.Builder()
                .url(GOOGLE_SPEECH_TO_TEXT_ENDPOINT)
                .post(sttPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        ).execute().use { response ->
            val responseBody = response.body!!.string()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to start STT: ${response.code} / $responseBody")
                throw IllegalStateException("STT request failed with code ${response.code}")
            }
            JSONObject(responseBody).getString("name")
        }

        val operationStatusUrl =
            "https://speech.googleapis.com/v1/operations/$operationId?key=${BuildConfig.GOOGLE_SPEECH_API_KEY}"

        repeat(MAX_POLLING_ATTEMPTS) {
            delay(POLLING_DELAY_MS)

            httpClient.newCall(Request.Builder().url(operationStatusUrl).build()).execute().use { pollingResponse ->
                val responseJsonString = pollingResponse.body!!.string()
                if (pollingResponse.isSuccessful) {
                    val pollingJson = JSONObject(responseJsonString)
                    Log.d(TAG, "Polling STT: $pollingJson")

                    if (pollingJson.optBoolean("done", false)) {
                        val responseJson = pollingJson.optJSONObject("response") ?: return@use
                        val resultsJsonArray = responseJson.optJSONArray("results") ?: return@use

                        val diarizedWordsList = (0 until resultsJsonArray.length())
                            .asSequence()
                            .flatMap { i ->
                                val alternativesArray = resultsJsonArray.getJSONObject(i).getJSONArray("alternatives")
                                (0 until alternativesArray.length()).asSequence().flatMap { j ->
                                    val wordsArray = alternativesArray.getJSONObject(j).optJSONArray("words")
                                    wordsArray?.let { wordArr ->
                                        (0 until wordArr.length()).asSequence().map { k -> wordArr.getJSONObject(k) }
                                    } ?: emptySequence()
                                }
                            }
                            .filter { it.has("speakerTag") }
                            .toList()

                        if (diarizedWordsList.isEmpty()) {
                            Log.e(TAG, "Diarization failed: no speaker-tagged words.")
                            return@withContext "No diarized words found (diarization failed)."
                        }

                        val diarizedTranscript: String = buildString {
                            var lastSpeaker = -1
                            for (wordObject in diarizedWordsList) {
                                val speakerId = wordObject.getInt("speakerTag")
                                val spokenWord = wordObject.getString("word")

                                if (speakerId != lastSpeaker) {
                                    if (lastSpeaker != -1) append("\n")
                                    append(if (speakerId == 1) "caller: " else "phone user: ")
                                    lastSpeaker = speakerId
                                }
                                append("$spokenWord ")
                            }
                        }.trim()

                        Log.d(TAG, "Final transcript:\n$diarizedTranscript")
                        return@withContext diarizedTranscript
                    }
                } else {
                    Log.e(TAG, "Polling failed: ${pollingResponse.code} / ${pollingResponse.body?.string()}")
                }
            }
        }

        throw TimeoutException("Transcription timed out after $MAX_POLLING_ATTEMPTS attempts.")
    }
}
