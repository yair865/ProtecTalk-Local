package com.example.protectalk.managers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionManager(
    private val context: Context,
    private val onTranscriptionReady: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private val TAG = "SpeechRecognitionManager"

    fun startListening() {
        Log.d(TAG, "Starting speech recognition...")

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString(" ") ?: ""
                    Log.d(TAG, "Recognized transcript: $transcript")
                    onTranscriptionReady(transcript)
                    restartListening()
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    restartListening()
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech...")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        recognizer?.startListening(createRecognizerIntent())
    }

    private fun restartListening() {
        recognizer?.cancel()
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Restarting listening...")
            recognizer?.startListening(createRecognizerIntent())
        }, 500)
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
    }

    fun stopListening() {
        Log.d(TAG, "Stopping speech recognition...")
        recognizer?.destroy()
    }
}
