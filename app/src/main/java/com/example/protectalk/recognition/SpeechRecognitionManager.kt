package com.example.protectalk.recognition

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.protectalk.utils.NetworkClient
import com.example.protectalk.notifications.AlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpeechRecognitionManager(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        // 1. Speakerphone on
        audioManager.isSpeakerphoneOn = true

        // 2. Create and start SpeechRecognizer
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            })
        }
    }

    fun stop() {
        recognizer?.apply {
            stopListening()
            destroy()
        }
        recognizer = null
        audioManager.isSpeakerphoneOn = false
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            // Optionally restart recognition if needed
        }
        override fun onPartialResults(bundle: Bundle) {
            bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.forEach { segment ->
                    analyzeSegment(segment)
                }
        }
        override fun onResults(bundle: Bundle) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun analyzeSegment(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val score = NetworkClient.getRiskScore(text)
            if (score > 0.7) {
                AlertNotifier.notify(context, score)
            }
        }
    }
}