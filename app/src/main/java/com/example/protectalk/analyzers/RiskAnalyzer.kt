package com.example.protectalk.analyzers

/*import android.util.Log
import com.example.protectalk.utils.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RiskAnalyzer(
    private val onProbabilityUpdated: (Double) -> Unit
) {
    private val TAG = "RiskAnalyzer"
    private val cumulativeTranscript = StringBuilder()

    *//**
     * Call this for each new transcript chunk.
     * Accumulates the text and calls your HF zero-shot classifier for an updated risk score.
     *//*
    suspend fun processTranscript(chunk: String) {
        // 1) accumulate
        if (cumulativeTranscript.isNotEmpty()) cumulativeTranscript.append(' ')
        cumulativeTranscript.append(chunk)
        val textSoFar = cumulativeTranscript.toString()
        Log.d(TAG, "Analyzing ${textSoFar.length} chars…")

        // 2) fetch risk score
        val score = withContext(Dispatchers.IO) {
            NetworkClient.getRiskScore(textSoFar)
        }
        Log.d(TAG, "Received scam probability: $score")

        // 3) emit the update
        onProbabilityUpdated(score)
    }
}*/
