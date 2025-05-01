package com.example.protectalk.analyzers

import android.util.Log
import com.example.protectalk.utils.ScamDetectionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RiskAnalyzer(private val onProbabilityUpdated: (Double) -> Unit) {

    private var currentProbability = 0.0
    private val TAG = "RiskAnalyzer"

    suspend fun processTranscript(transcript: String) {
        Log.d(TAG, "Processing new transcript: $transcript")

        val pulseProbability = withContext(Dispatchers.IO) {
            ScamDetectionApi.getScamProbability(transcript)
        }

        Log.d(TAG, "Pulse probability received: $pulseProbability")

        currentProbability += (pulseProbability * 0.5)
        if (currentProbability > 1.0) currentProbability = 1.0

        Log.d(TAG, "Updated cumulative probability: $currentProbability")

        onProbabilityUpdated(currentProbability)
    }
}
