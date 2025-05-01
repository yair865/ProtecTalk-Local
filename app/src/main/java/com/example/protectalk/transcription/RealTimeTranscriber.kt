package com.example.protectalk.transcription

import android.content.Context
import android.util.Log
import com.example.protectalk.utils.copyAssetsTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream

class RealTimeTranscriber(private val context: Context) {
    private companion object {
        const val TAG = "RealTimeTranscriber"
    }

    private var model: Model? = null

    /** Copies the Vosk model from assets into filesDir and loads it on first use */
    private fun ensureModelLoaded() {
        if (model != null) return
        val modelDir = File(context.filesDir, "vosk-model-small")
        if (!modelDir.exists()) {
            context.assets.copyAssetsTo(modelDir.absolutePath)
        }
        model = Model(modelDir.absolutePath)
        Log.d(TAG, "Vosk model loaded from ${modelDir.absolutePath}")
    }

    /**
     * Transcribes the given audio file (PCM/WAV/MP4) to text, offline via Vosk.
     */
    suspend fun transcribe(file: File): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Transcribing file: ${file.absolutePath}")
        ensureModelLoaded()
        val voskModel = model ?: throw IllegalStateException("Vosk model not initialized.")
        val recognizer = Recognizer(voskModel, 16000.0f)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } > 0) {
                recognizer.acceptWaveForm(buffer, bytesRead)
            }
        }
        val resultJson = recognizer.finalResult
        val text = JSONObject(resultJson).optString("text", "")
        Log.d(TAG, "Recognition result: \"$text\"")
        text
    }
}
