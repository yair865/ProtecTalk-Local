package com.example.protectalk.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.protectalk.R

object AlertNotifier {
    private const val CHANNEL_ID = "scam_alerts"
    private const val NOTIF_ID = 1001

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun notify(context: Context, score: Double) {
        // 1) Get the NotificationManager for channel creation
        val notificationManager =
            context.getSystemService(NotificationManager::class.java)!!

        // 2) Create the channel on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-risk call alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 3) Choose traffic-light color
        val color = when {
            score >= 0.8 -> Color.RED
            score >= 0.4 -> Color.YELLOW
            else         -> Color.GREEN
        }

        // 4) Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)      // ensure you have res/drawable/ic_alert.xml
            .setContentTitle("Scam risk: ${"%.2f".format(score)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(color)
            .setVibrate(longArrayOf(0, 500, 100, 500))
            .build()

        // 5) Dispatch via NotificationManagerCompat
        NotificationManagerCompat.from(context)
            .notify(NOTIF_ID, notification)
    }
}
