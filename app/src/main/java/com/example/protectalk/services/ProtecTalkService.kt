// ProtecTalkService.kt
package com.example.protectalk.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.example.protectalk.transcription.RealTimeTranscriber
import kotlinx.coroutines.*
import java.io.File

class ProtecTalkService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var phoneMgr: TelephonyManager
    private var previousState = TelephonyManager.CALL_STATE_IDLE

    companion object {
        const val ACTION_TRANSCRIPT = "com.example.protectalk.TRANSCRIPT_READY"
        const val EXTRA_TEXT       = "transcript_text"
        private const val CH_ID    = "protecTalkChannel"
        private const val N_ID     = 1337
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(N_ID, buildNotification("ProtecTalk: waiting for call…"))

        phoneMgr = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        phoneMgr.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    private val callListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            if (previousState == TelephonyManager.CALL_STATE_OFFHOOK
                && state == TelephonyManager.CALL_STATE_IDLE) {
                serviceScope.launch { handleCallEnded() }
            }
            previousState = state
        }
    }

    private suspend fun handleCallEnded() {
        val lastFile = findLatestRecording()
        val transcript = if (lastFile != null) {
            try {
                RealTimeTranscriber().transcribe(lastFile)
            } catch (e: Exception) {
                "Transcription failed: ${e.localizedMessage}"
            }
        } else {
            "No recording found."
        }

        sendBroadcast(Intent(ACTION_TRANSCRIPT).apply {
            putExtra(EXTRA_TEXT, transcript)
        })
    }

    private fun findLatestRecording(): File? {
        val base = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(base, "CallRecordings"),
            File(base, "Android/data/com.android.soundrecorder/files"),
            File(base, "MIUI/sound_recorder/call_rec"),
            File(base, "MIUI/sound_recorder"),
            File(base, "Recordings")
        )
        val exts = setOf("amr", "m4a", "wav", "3gp", "mp4")
        return dirs.asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension.lowercase() in exts }
            .maxByOrNull { it.lastModified() }
    }

    override fun onDestroy() {
        super.onDestroy()
        @Suppress("DEPRECATION")
        phoneMgr.listen(null, 0)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "ProtecTalk Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentText(text)
            .setOngoing(true)
            .build()
}
