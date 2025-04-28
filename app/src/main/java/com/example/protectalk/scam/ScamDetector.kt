// In com/example/protectalk/scam/ScamDetector.kt
package com.example.protectalk.scam

object ScamDetector {
    /**
     * Returns a risk score [0.0, 1.0] based on the content.
     * Right now it just checks for keywords to demo the flow.
     */
    fun analyzeTranscription(text: String): Double {
        val lower = text.lowercase()
        return when {
            "gift card" in lower || "urgent" in lower -> 0.85
            else                                        -> 0.15
        }
    }
}
