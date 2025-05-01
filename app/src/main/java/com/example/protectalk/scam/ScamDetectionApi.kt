package com.example.protectalk.utils

object ScamDetectionApi {
    suspend fun getScamProbability(transcript: String): Double {
        return NetworkClient.getRiskScore(transcript)
    }
}
