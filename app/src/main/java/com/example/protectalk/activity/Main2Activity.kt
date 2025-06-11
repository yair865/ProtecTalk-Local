package com.example.protectalk.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.protectalk.databinding.ActivityMain2Binding
import com.example.protectalk.services.ProtecTalkService
import com.google.android.material.snackbar.Snackbar

/**
 * Main entry point of the ProtecTalk app.
 * Handles permission requests, UI interactions, and receives scam analysis broadcast.
 */
class Main2Activity : AppCompatActivity() {

    // == Constants ==
    private companion object {
        const val PERMISSION_RATIONALE_MESSAGE = "Essential permissions are required. Please grant them."
        const val PERMISSION_SETTINGS_REDIRECT_MESSAGE = "Permissions permanently denied. Enable in settings."
        const val PERMISSION_GRANTED_SNACKBAR_MESSAGE = "ProtecTalk Service started"
        const val PERMISSION_DENIED_SNACKBAR_MESSAGE = "Permissions missing."
        const val CALL_RECORDING_SETTINGS_MESSAGE =
            "Enable auto-record unknown callers in Phone → Settings → Record calls"

        const val RED_COLOR: Int = Color.RED
        const val WHITE_COLOR: Int = Color.WHITE
    }

    // == View Binding ==
    private lateinit var binding: ActivityMain2Binding

    // == Permissions Launcher ==
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val phoneStateGranted = results[Manifest.permission.READ_PHONE_STATE] == true
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (phoneStateGranted && micGranted && notificationsGranted && storageGranted) {
            startProtecTalkService()
        } else {
            showPersistentSnackbar(PERMISSION_RATIONALE_MESSAGE, "Grant") {
                handlePermissionDenial()
            }
        }
    }

    // == Broadcast Receiver ==
    private val transcriptBroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            val transcriptText: String = intent.getStringExtra(ProtecTalkService.EXTRA_KEY_TRANSCRIPT_TEXT).orEmpty()
            val scamScore: Int = intent.getIntExtra(ProtecTalkService.EXTRA_KEY_SCAM_SCORE, 0)
            val analysisDetails: String = intent
                .getStringArrayListExtra(ProtecTalkService.EXTRA_KEY_ANALYSIS_DETAILS)
                ?.joinToString("\n").orEmpty()

            binding.tvTranscript.text = transcriptText
            binding.tvScore.text = "$scamScore % scam-risk"
            binding.tvAnalysis.text = analysisDetails

            if (scamScore >= ProtecTalkService.SCAM_ALERT_SCORE_THRESHOLD) {
                binding.tvScore.setTextColor(RED_COLOR)
                binding.tvScore.setTypeface(null, Typeface.BOLD)

                Snackbar.make(binding.root, "⚠️ Scam risk detected!", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(RED_COLOR)
                    .setTextColor(WHITE_COLOR)
                    .show()
            } else {
                binding.tvScore.setTextColor(WHITE_COLOR)
                binding.tvScore.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    // == Lifecycle ==
    @Suppress("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissionsAndStartServiceIfGranted()
        promptEnableCallRecording()

        // Register receiver for analysis broadcast
        val transcriptIntentFilter = IntentFilter(ProtecTalkService.BROADCAST_ACTION_TRANSCRIPT_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(transcriptBroadcastReceiver, transcriptIntentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(transcriptBroadcastReceiver, transcriptIntentFilter)
        }

        // FAB to manually start service
        binding.fabStart.setOnClickListener {
            if (hasRequiredPermissions()) {
                startProtecTalkService()
            } else {
                Snackbar.make(binding.root, PERMISSION_DENIED_SNACKBAR_MESSAGE, Snackbar.LENGTH_LONG).show()
                requestAllPermissions()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(transcriptBroadcastReceiver)
    }

    // == Permissions ==
    /**
     * Checks required permissions and starts service if all are granted.
     */
    private fun checkPermissionsAndStartServiceIfGranted() {
        if (hasRequiredPermissions()) startProtecTalkService()
        else requestAllPermissions()
    }

    /**
     * Requests all runtime permissions required for app functionality.
     */
    private fun requestAllPermissions() {
        val requiredPermissions = mutableListOf(Manifest.permission.READ_PHONE_STATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions += Manifest.permission.POST_NOTIFICATIONS
            requiredPermissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            requiredPermissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        requiredPermissions += Manifest.permission.RECORD_AUDIO
        permissionsLauncher.launch(requiredPermissions.toTypedArray())
    }

    /**
     * Checks if all required permissions are currently granted.
     */
    private fun hasRequiredPermissions(): Boolean {
        val context = this

        val phoneGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }

        return phoneGranted && micGranted && notifyGranted && storageGranted
    }

    /**
     * Handles permission denial by showing rationale or redirecting to app settings.
     */
    private fun handlePermissionDenial() {
        if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
            showPersistentSnackbar(PERMISSION_SETTINGS_REDIRECT_MESSAGE, "Settings") {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(settingsIntent)
            }
        } else {
            requestAllPermissions()
        }
    }

    // == UI Helpers ==
    /**
     * Shows a persistent snackbar with an action button.
     */
    private fun showPersistentSnackbar(message: String, actionLabel: String, onClick: () -> Unit) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
            .setAction(actionLabel) { onClick() }
            .show()
    }

    /**
     * Prompts user to enable auto-call recording in system settings.
     */
    private fun promptEnableCallRecording() {
        Snackbar.make(binding.root, CALL_RECORDING_SETTINGS_MESSAGE, Snackbar.LENGTH_INDEFINITE)
            .setAction("Open Settings") {
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName(
                            "com.samsung.android.dialer",
                            "com.samsung.android.dialer.settings.RecordingSettingsActivity"
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:com.samsung.android.dialer".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
            .show()
    }

    /**
     * Starts the ProtecTalk background service.
     */
    private fun startProtecTalkService() {
        startService(Intent(this, ProtecTalkService::class.java))
        Snackbar.make(binding.root, PERMISSION_GRANTED_SNACKBAR_MESSAGE, Snackbar.LENGTH_SHORT).show()
    }
}
