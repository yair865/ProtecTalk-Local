package com.example.protectalk.notifications

//noinspection SuspiciousImport
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val ALERT_CHANNEL_ID = "scamAlertChannel"
    private const val ALERT_NOTIFICATION_ID = 1338

    fun createAlertChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Scam Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                }
            )
        }
    }

    fun sendAlert(ctx: Context, score: Int, analysis: String) {
        val notif = NotificationCompat.Builder(ctx, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Scam Risk: $score/100")
            .setContentText(analysis)
            .setStyle(NotificationCompat.BigTextStyle().bigText(analysis))
            .setColor(Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        ctx.getSystemService(NotificationManager::class.java)
            .notify(ALERT_NOTIFICATION_ID, notif)
    }
}