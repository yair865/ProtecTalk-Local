package com.example.protectalk.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val ALERT_CHANNEL_ID    = "scamAlertChannel"
    private const val ALERT_NOTIFICATION_ID = 1338

    fun createAlertChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating alert channel")
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
        Log.d(TAG, "sendAlert() score=$score")
        val notification = NotificationCompat.Builder(ctx, ALERT_CHANNEL_ID)
            // use a built-in alert icon
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Scam Risk: $score/100")
            .setContentText(analysis)
            .setStyle(NotificationCompat.BigTextStyle().bigText(analysis))
            .setColor(Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        ctx.getSystemService(NotificationManager::class.java)
            .notify(ALERT_NOTIFICATION_ID, notification)
    }
}
