package com.example.protectalk.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.example.protectalk.BuildConfig
import com.example.protectalk.analysis.ChatGPTAnalyzer
import com.example.protectalk.transcription.SpeechTranscriber
import com.example.protectalk.notifications.NotificationHelper
import com.example.protectalk.utils.RecordingFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProtecTalkService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var phoneMgr: TelephonyManager
    private var prevState = TelephonyManager.CALL_STATE_IDLE

    private val transcriber by lazy { SpeechTranscriber(BuildConfig.GOOGLE_API_KEY) }
    private val analyzer   by lazy { ChatGPTAnalyzer(BuildConfig.OPENAI_API_KEY) }

    companion object {
        private const val FG_CHANNEL_ID       = "protecTalkForeground"
        private const val FG_NOTIFICATION_ID  = 1337
        const val ALERT_THRESHOLD = 60
        const val ACTION_TRANSCRIPT  = "com.example.protectalk.TRANSCRIPT_READY"
        const val EXTRA_TRANSCRIPT   = "transcript_text"
        const val EXTRA_SCORE        = "scam_score"
        const val EXTRA_ANALYSIS     = "analysis_text"
    }

    override fun onCreate() {
        super.onCreate()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    private val callListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            if (prevState == TelephonyManager.CALL_STATE_OFFHOOK && state == TelephonyManager.CALL_STATE_IDLE) {
                scope.launch { processCall() }
            }
            prevState = state
        }
    }

    private suspend fun processCall() {
        val file = RecordingFinder.findLatestRecording()
        val transcript = file?.let { transcriber.transcribe(it) } ?: "No recording found."
        val (score, analysis) = analyzer.analyze(transcript)

        // broadcast results
        sendBroadcast(Intent(ACTION_TRANSCRIPT).apply {
            putExtra(EXTRA_TRANSCRIPT, transcript)
            putExtra(EXTRA_SCORE, score)
            putExtra(EXTRA_ANALYSIS, analysis)
        })

        if (score >= ALERT_THRESHOLD) {
            NotificationHelper.sendAlert(this@ProtecTalkService, score, analysis)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        @Suppress("DEPRECATION")
        phoneMgr.listen(null, 0)
        job.cancel()
    }

    override fun onBind(intent: Intent?) = null

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