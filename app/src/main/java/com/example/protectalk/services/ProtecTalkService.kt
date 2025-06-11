@file:Suppress("DEPRECATION")

package com.example.protectalk.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.protectalk.BuildConfig
import com.example.protectalk.analysis.ChatGPTAnalyzer
import com.example.protectalk.analysis.ScamResult
import com.example.protectalk.notifications.ScamNotificationManager
import com.example.protectalk.transcription.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A foreground Android service that monitors phone call state,
 * performs audio transcription after a call, and analyzes for scam risk.
 */
class ProtecTalkService : Service() {

    // == Logging Tag ==
    private val logTag: String = "ProtecTalkService"

    // == Coroutine Scope ==
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // == Telephony ==
    private lateinit var telephonyManager: TelephonyManager
    private var previousCallState: Int = TelephonyManager.CALL_STATE_IDLE

    private var modernTelephonyCallback: TelephonyCallbackCallStateListener? = null
    private var legacyPhoneStateListener: PhoneStateListener? = null

    // == Core Components ==
    private val transcriber: Transcriber by lazy { Transcriber() }
    private val analyzer: ChatGPTAnalyzer by lazy { ChatGPTAnalyzer(BuildConfig.OPENAI_API_KEY) }

    companion object {
        // == Scam Detection Threshold ==
        const val SCAM_ALERT_SCORE_THRESHOLD: Int = 60

        // == Intent Actions and Extras ==
        const val BROADCAST_ACTION_TRANSCRIPT_READY: String = "com.example.protectalk.TRANSCRIPT_READY"
        const val EXTRA_KEY_TRANSCRIPT_TEXT: String = "transcript_text"
        const val EXTRA_KEY_SCAM_SCORE: String = "scam_score"
        const val EXTRA_KEY_ANALYSIS_DETAILS: String = "analysis_list"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(logTag, "Service created")

        // Create the notification channel for scam alerts
        ScamNotificationManager.createScamAlertNotificationChannel(this)

        // Initialize telephony manager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernTelephonyCallback = TelephonyCallbackCallStateListener()
            telephonyManager.registerTelephonyCallback(mainExecutor, modernTelephonyCallback!!)
        } else {
            legacyPhoneStateListener = object : PhoneStateListener() {
                @RequiresApi(Build.VERSION_CODES.S)
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChange(state)
                }
            }

            @Suppress("DEPRECATION")
            telephonyManager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(logTag, "Service destroyed")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernTelephonyCallback?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
        } else {
            @Suppress("DEPRECATION")
            legacyPhoneStateListener?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }

        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Handles call state transitions.
     * If a call has just ended, starts transcription and scam analysis.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleCallStateChange(newCallState: Int) {
        if (previousCallState == TelephonyManager.CALL_STATE_OFFHOOK &&
            newCallState == TelephonyManager.CALL_STATE_IDLE
        ) {
            Log.d(logTag, "Call ended — initiating transcription and analysis.")
            serviceScope.launch { processCompletedCallAudio() }
        }
        previousCallState = newCallState
    }

    /**
     * Transcribes the last call audio and analyzes it for scam detection.
     * Sends broadcast with the result and optionally triggers a notification.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun processCompletedCallAudio() {
        Log.d(logTag, "processCompletedCallAudio() started")

        val callTranscript: String = withContext(Dispatchers.IO) {
            try {
                transcriber.transcribeFromLatestFile()
            } catch (e: Exception) {
                Log.e(logTag, "Transcription failed: ${e.message}", e)
                "Transcription error or timeout."
            }
        }

        val scamResult: ScamResult = try {
            analyzer.analyze(callTranscript)
        } catch (e: Exception) {
            Log.e(logTag, "Analysis failed: ${e.message}", e)
            ScamResult(
                score = 0,
                analysisPoints = listOf("Analysis error: ${e.message}")
            )
        }

        val transcriptReadyIntent = Intent(BROADCAST_ACTION_TRANSCRIPT_READY).apply {
            putExtra(EXTRA_KEY_TRANSCRIPT_TEXT, callTranscript)
            putExtra(EXTRA_KEY_SCAM_SCORE, scamResult.score)
            putStringArrayListExtra(EXTRA_KEY_ANALYSIS_DETAILS, ArrayList(scamResult.analysisPoints))
        }

        sendBroadcast(transcriptReadyIntent)

        if (scamResult.score >= SCAM_ALERT_SCORE_THRESHOLD) {
            ScamNotificationManager.showScamCallDetectedNotification(
                applicationContext,
                scamResult.score,
                scamResult.analysisPoints
            )
        } else {
            ScamNotificationManager.showSafeCallNotification(applicationContext)
        }

        Log.d(logTag, "processCompletedCallAudio() complete")
    }

    /**
     * Modern TelephonyCallback implementation for Android 12+.
     * Listens for call state changes.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private inner class TelephonyCallbackCallStateListener : TelephonyCallback(),
        TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallStateChange(state)
        }
    }
}
