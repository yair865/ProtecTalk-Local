// AlertNotifier.kt
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
import android.util.Log

object AlertNotifier {
    private const val CHANNEL_ID = "scam_alerts"
    private const val WARNING_ID = 1001
    private const val ALERT_ID   = 1002
    private const val TAG = "AlertNotifier"

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun warn(context: Context, probability: Double) {
        show(context, probability, isAlert = false)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun alert(context: Context, probability: Double) {
        show(context, probability, isAlert = true)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun show(context: Context, prob: Double, isAlert: Boolean) {
        Log.d(TAG, "notify(${if (isAlert) "ALERT" else "WARN"}): $prob")

        // Create channel once (idempotent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time scam call alerts"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }

        val title = if (isAlert) "⚠️ SCAM Detected!" else "⚠️ Potential Scam"
        val color = if (isAlert) Color.RED else Color.YELLOW
        val msg = "Risk: ${"%.1f".format(prob * 100)}%"

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(msg)
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .build()

        NotificationManagerCompat.from(context)
            .notify(if (isAlert) ALERT_ID else WARNING_ID, notif)
    }
}
