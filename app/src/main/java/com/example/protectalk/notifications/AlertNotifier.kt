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
    private const val WARNING_NOTIF_ID = 1001
    private const val ALERT_NOTIF_ID = 1002
    private const val TAG = "AlertNotifier"

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun warn(context: Context, probability: Double) {
        Log.d(TAG, "Sending WARNING notification with risk: $probability")
        showNotification(
            context,
            probability,
            isAlert = false
        )
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun alert(context: Context, probability: Double) {
        Log.d(TAG, "Sending ALERT notification with risk: $probability")
        showNotification(
            context,
            probability,
            isAlert = true
        )
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showNotification(context: Context, probability: Double, isAlert: Boolean) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the notification channel if needed (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time scam call alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Traffic light color based on alert type
        val color = when {
            isAlert -> Color.RED
            else    -> Color.YELLOW
        }

        val title = if (isAlert) {
            "⚠️ SCAM Likely Detected!"
        } else {
            "⚠️ Potential Scam Warning"
        }

        val message = "Risk Score: ${"%.2f".format(probability)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        NotificationManagerCompat.from(context).notify(
            if (isAlert) ALERT_NOTIF_ID else WARNING_NOTIF_ID,
            notification
        )
    }
}
