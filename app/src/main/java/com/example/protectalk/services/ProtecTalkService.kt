@file:Suppress("DEPRECATION")

package com.example.protectalk.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.protectalk.BuildConfig
import com.example.protectalk.analysis.ChatGPTAnalyzer
import com.example.protectalk.notifications.NotificationHelper
import com.example.protectalk.transcription.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProtecTalkService : Service() {
    private val TAG = "ProtecTalkService"

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var phoneMgr: TelephonyManager
    private var prevState: Int = TelephonyManager.CALL_STATE_IDLE

    private var callStateCallback: TelephonyCallbackCallStateListener? = null
    private var legacyPhoneStateListener: PhoneStateListener? = null

    private val transcriber by lazy { Transcriber() }
    private val analyzer by lazy { ChatGPTAnalyzer(BuildConfig.OPENAI_API_KEY) }

    companion object {
        const val ALERT_THRESHOLD = 60
        const val ACTION_TRANSCRIPT = "com.example.protectalk.TRANSCRIPT_READY"
        const val EXTRA_TRANSCRIPT = "transcript_text"
        const val EXTRA_SCORE = "scam_score"
        const val EXTRA_ANALYSIS = "analysis_list"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        NotificationHelper.createNotificationChannel(this)

        phoneMgr = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callStateCallback = TelephonyCallbackCallStateListener()
            phoneMgr.registerTelephonyCallback(mainExecutor, callStateCallback!!)
        } else {
            legacyPhoneStateListener = object : PhoneStateListener() {
                @RequiresApi(Build.VERSION_CODES.S)
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChange(state)
                }
            }
            @Suppress("DEPRECATION")
            phoneMgr.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callStateCallback?.let { phoneMgr.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            legacyPhoneStateListener?.let { phoneMgr.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleCallStateChange(state: Int) {
        if (prevState == TelephonyManager.CALL_STATE_OFFHOOK &&
            state == TelephonyManager.CALL_STATE_IDLE
        ) {
            Log.d(TAG, "Call ended – starting transcription+analysis flow")
            scope.launch { processCall() }
        }
        prevState = state
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun processCall() {
        Log.d(TAG, "processCall() start")

        val transcript = withContext(Dispatchers.IO) {
            try {
                transcriber.transcribeFromLatestFile()
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed: ${e.message}", e)
                "Transcription error or timeout."
            }
        }

        val scamResult = try {
            analyzer.analyze(transcript)
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed: ${e.message}", e)
            com.example.protectalk.analysis.ScamResult(
                score = 0,
                analysisPoints = listOf("Analysis error: ${e.message}")
            )
        }

        val broadcast = Intent(ACTION_TRANSCRIPT).apply {
            putExtra(EXTRA_TRANSCRIPT, transcript)
            putExtra(EXTRA_SCORE, scamResult.score)
            putStringArrayListExtra(EXTRA_ANALYSIS, ArrayList(scamResult.analysisPoints))
        }
        sendBroadcast(broadcast)

        if (scamResult.score >= ALERT_THRESHOLD) {
            NotificationHelper.sendAlert(this@ProtecTalkService, scamResult.score, scamResult.analysisPoints)
        }
        else{
            NotificationHelper.sendSafeAlert(this@ProtecTalkService)
        }

        Log.d(TAG, "processCall() end")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class TelephonyCallbackCallStateListener : TelephonyCallback(),
        TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallStateChange(state)
        }
    }
}