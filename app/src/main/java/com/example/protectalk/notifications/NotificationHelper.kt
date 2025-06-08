package com.example.protectalk.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.protectalk.R

object NotificationHelper {
    const val CHANNEL_ID = "protectalk_alerts_v2" // CHANGED ID

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ProtecTalk Scam Alerts", // Name for users
                NotificationManager.IMPORTANCE_HIGH // Must be HIGH for heads-up!
            )
            channel.description = "Urgent scam call alerts"
            channel.enableVibration(true)
            channel.enableLights(true)
            channel.lightColor = Color.RED
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun sendAlert(context: Context, score: Int, analysis: List<String>) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("⚠️ Potential Scam Call Detected!")
            .setContentText("Risk Score: $score\n" + analysis.joinToString("\n"))
            .setStyle(NotificationCompat.BigTextStyle().bigText("Risk Score: $score\n" + analysis.joinToString("\n")))
            .setSmallIcon(R.drawable.ic_warning_red)
            .setColor(Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Enables sound/vibration
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, notification)
    }
}
