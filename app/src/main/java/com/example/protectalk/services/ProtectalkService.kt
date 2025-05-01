package com.example.protectalk.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.protectalk.R
import com.example.protectalk.analyzers.RiskAnalyzer
import com.example.protectalk.notifications.AlertNotifier
import com.example.protectalk.managers.CallRecorderManager
import com.example.protectalk.transcription.RealTimeTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ProtectalkService : Service() {

    private lateinit var recorderManager: CallRecorderManager
    private lateinit var transcriber: RealTimeTranscriber
    private lateinit var riskAnalyzer: RiskAnalyzer
    private val scope = CoroutineScope(Dispatchers.Main)
    private val TAG = "ProtectalkService"

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ProtectalkService created.")

        // 1) Start foreground to keep service alive during recording
        startForeground(1, createNotification("ProtecTalk Active"))

        // 2) Initialize risk analyzer with notification callbacks
        riskAnalyzer = RiskAnalyzer { probability ->
            when {
                probability in 0.4..0.55 -> {
                    Log.d(TAG, "Warning: probability in warning range ($probability)")
                    AlertNotifier.warn(this, probability)
                }
                probability > 0.55 -> {
                    Log.d(TAG, "ALERT: probability above threshold ($probability)")
                    AlertNotifier.alert(this, probability)
                }
            }
        }

        // 3) Set up offline transcriber
        transcriber = RealTimeTranscriber(this)

        // 4) Set up call recorder manager
        recorderManager = CallRecorderManager(
            context = this,
            chunkCallback = { file ->
                // For each 12-second chunk file, transcribe then analyze
                scope.launch {
                    val transcript = transcriber.transcribe(file)
                    if (transcript.isNotBlank()) {
                        Log.d(TAG, "Transcript: \"$transcript\"")
                        riskAnalyzer.processTranscript(transcript)
                    } else {
                        Log.d(TAG, "Empty transcript for ${file.name}")
                    }
                }
            },
            errorCallback = { e ->
                Log.e(TAG, "CallRecorder error", e)
                // Optionally, you could stop service or switch to speaker-mode fallback here
            }
        )

        // 5) Start recording in-call audio
        recorderManager.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ProtectalkService destroyed.")
        // Stop the recorder and cancel any pending coroutine work
        recorderManager.stop()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Builds the minimal foreground notification required on Android O+.
     */
    private fun createNotification(content: String): Notification {
        val channelId = "protectalk_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ProtecTalk Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("ProtecTalk Active")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_alert)
            .build()
    }
}
