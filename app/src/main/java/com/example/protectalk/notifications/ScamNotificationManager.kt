package com.example.protectalk.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.protectalk.R

/**
 * Handles creation and display of scam and safe call notifications for ProtecTalk.
 */
object ScamNotificationManager {

    // == Notification Channel Constants ==
    const val SCAM_ALERT_NOTIFICATION_CHANNEL_ID: String = "protectalk_alerts_v2"
    private const val SCAM_ALERT_NOTIFICATION_CHANNEL_NAME: String = "ProtecTalk Scam Alerts"
    private const val SCAM_ALERT_NOTIFICATION_CHANNEL_DESCRIPTION: String = "Urgent scam call alerts"

    // == Notification ID Constants ==
    private const val NOTIFICATION_ID_SCAM_CALL_DETECTED: Int = 2
    private const val NOTIFICATION_ID_SAFE_CALL: Int = 3

    // == Notification Text Constants ==
    private const val NOTIFICATION_TITLE_SCAM_CALL: String = "⚠️ Potential Scam Call Detected!"
    private const val NOTIFICATION_TITLE_SAFE_CALL: String = "✅ Call Safe"
    private const val NOTIFICATION_BODY_SAFE_CALL: String = "No scam risk detected. This call is safe!"

    /**
     * Creates the notification channel required for scam alerts on Android O+.
     *
     * @param applicationContext The context used to access system services.
     */
    fun createScamAlertNotificationChannel(applicationContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val scamAlertNotificationChannel: NotificationChannel = NotificationChannel(
                SCAM_ALERT_NOTIFICATION_CHANNEL_ID,
                SCAM_ALERT_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = SCAM_ALERT_NOTIFICATION_CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
                lightColor = Color.RED
            }

            val systemNotificationManager: NotificationManager =
                applicationContext.getSystemService(NotificationManager::class.java)

            systemNotificationManager.createNotificationChannel(scamAlertNotificationChannel)
        }
    }

    /**
     * Sends a notification indicating that a potential scam call was detected.
     *
     * @param applicationContext The context used to access system services.
     * @param scamRiskScore The score representing how risky the call is.
     * @param scamAnalysisDetails A list of analysis details explaining the risk.
     */
    fun showScamCallDetectedNotification(
        applicationContext: Context,
        scamRiskScore: Int,
        scamAnalysisDetails: List<String>
    ) {
        val detailedScamMessage =
            "Risk Score: $scamRiskScore\n${scamAnalysisDetails.joinToString(separator = "\n")}"

        val scamAlertNotification: android.app.Notification = NotificationCompat.Builder(
            applicationContext,
            SCAM_ALERT_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(NOTIFICATION_TITLE_SCAM_CALL)
            .setContentText(detailedScamMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailedScamMessage))
            .setSmallIcon(R.drawable.ic_warning_red)
            .setColor(Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val systemNotificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        systemNotificationManager.notify(
            NOTIFICATION_ID_SCAM_CALL_DETECTED,
            scamAlertNotification
        )
    }

    /**
     * Sends a notification to reassure the user that the current call is safe.
     *
     * @param applicationContext The context used to access system services.
     */
    fun showSafeCallNotification(applicationContext: Context) {
        val safeCallNotification: android.app.Notification = NotificationCompat.Builder(
            applicationContext,
            SCAM_ALERT_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(NOTIFICATION_TITLE_SAFE_CALL)
            .setContentText(NOTIFICATION_BODY_SAFE_CALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIFICATION_BODY_SAFE_CALL))
            .setSmallIcon(R.drawable.ic_check_circle_green)
            .setColor(Color.GREEN)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        val systemNotificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        systemNotificationManager.notify(
            NOTIFICATION_ID_SAFE_CALL,
            safeCallNotification
        )
    }
}
