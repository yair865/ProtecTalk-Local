// File: com/example/protectalk/notifications/NotificationHelper.kt

package com.example.protectalk.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.protectalk.R

object NotificationHelper {
    const val CHANNEL_ID = "protectalk_service"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ProtecTalk Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("ProtecTalk Active")
            .setContentText("Analyzing calls for scams.")
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure ic_launcher exists
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun sendAlert(context: Context, score: Int, analysis: List<String>) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Potential Scam Call Detected!")
            .setContentText("Risk Score: $score\n" + analysis.joinToString("\n"))
            .setSmallIcon(R.mipmap.ic_launcher) // Or your preferred icon
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, notification)
    }
}
