package com.example.protectalk.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.nfc.Tag
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.protectalk.BuildConfig
import com.example.protectalk.analysis.ChatGPTAnalyzer
import com.example.protectalk.notifications.NotificationHelper
import com.example.protectalk.transcription.RealTimeTranscriber
import com.example.protectalk.utils.RecordingFinder
import kotlinx.coroutines.*

class ProtecTalkService : Service() {
    private val TAG = "ProtecTalkService"

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var phoneMgr: TelephonyManager
    private var prevState = TelephonyManager.CALL_STATE_IDLE

    private val transcriber by lazy { RealTimeTranscriber() }
    private val analyzer   by lazy { ChatGPTAnalyzer(BuildConfig.OPENAI_API_KEY) }

    companion object {
        private const val FG_CHANNEL_ID      = "protecTalkForeground"
        private const val FG_NOTIFICATION_ID = 1337
        const val ALERT_THRESHOLD           = 70
        const val ACTION_TRANSCRIPT         = "com.example.protectalk.TRANSCRIPT_READY"
        const val EXTRA_TRANSCRIPT          = "transcript_text"
        const val EXTRA_SCORE               = "scam_score"
        const val EXTRA_ANALYSIS            = "analysis_text"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createForegroundChannel()
        NotificationHelper.createAlertChannel(this)
        startForeground(
            FG_NOTIFICATION_ID,
            buildForegroundNotification("ProtecTalk: waiting for call…")
        )

        phoneMgr = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        phoneMgr.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    private val callListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            if (prevState == TelephonyManager.CALL_STATE_OFFHOOK
                && state == TelephonyManager.CALL_STATE_IDLE
            ) {
                Log.d(TAG, "Call ended, processing")
                scope.launch { processCall() }
            }
            prevState = state
        }
    }

    private suspend fun processCall() {
        Log.d(TAG, "processCall() start")

        // 1) Do file + network work on IO
        val (transcript, score, analysis) = withContext(Dispatchers.IO) {
            Log.d(TAG, "Finding latest recording")
            val file = RecordingFinder.findLatestRecording()

            Log.d(TAG, "Transcribing audio")
            val text = file?.let { transcriber.transcribe(it) }
                ?: "No recording found."
            Log.d(TAG, "Transcription: $text")
            Log.d(TAG, "Analyzing transcript with ChatGPT")
            val result = analyzer.analyze(text)

            Triple(text, result.score, result.analysis)
        }

        // 2) Broadcast results
        Log.d(TAG, "Broadcasting results")
        sendBroadcast(Intent(ACTION_TRANSCRIPT).apply {
            putExtra(EXTRA_TRANSCRIPT, transcript)
            putExtra(EXTRA_SCORE, score)
            putExtra(EXTRA_ANALYSIS, analysis)
        })

        // 3) Send alert if needed
        if (score >= ALERT_THRESHOLD) {
            Log.d(TAG, "Score $score ≥ $ALERT_THRESHOLD, sending alert")
            NotificationHelper.sendAlert(this@ProtecTalkService, score, analysis)
        } else {
            Log.d(TAG, "Score $score < $ALERT_THRESHOLD, no alert")
        }

        Log.d(TAG, "processCall() end")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        @Suppress("DEPRECATION")
        phoneMgr.listen(null, 0)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    FG_CHANNEL_ID,
                    "ProtecTalk Service",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildForegroundNotification(text: String): Notification =
        NotificationCompat.Builder(this, FG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(text)
            .setOngoing(true)
            .build()
}
