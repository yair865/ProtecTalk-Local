package com.example.protectalk.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.example.protectalk.R
import com.example.protectalk.recognition.SpeechRecognitionManager

class CallListenerService : Service() {
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var speechManager: SpeechRecognitionManager

    override fun onCreate() {
        super.onCreate()
        speechManager = SpeechRecognitionManager(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // Create a minimal notification channel & foreground notification
        val channelId = "service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId, "ProtecTalk Service",
                NotificationManager.IMPORTANCE_NONE
            )
            (getSystemService(NotificationManager::class.java))
                .createNotificationChannel(chan)
        }
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("ProtecTalk running")
            .setSmallIcon(R.drawable.ic_service)
            .build()
        startForeground(1, notif)

        // Listen for call state changes
        telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private val callStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> speechManager.start()
                TelephonyManager.CALL_STATE_IDLE   -> speechManager.stop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}