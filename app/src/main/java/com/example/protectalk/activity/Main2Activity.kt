package com.example.protectalk.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.protectalk.databinding.ActivityMain2Binding
import com.example.protectalk.services.ProtecTalkService
import com.google.android.material.snackbar.Snackbar
import androidx.core.net.toUri

class Main2Activity : AppCompatActivity() {
    private lateinit var binding: ActivityMain2Binding

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val okPhoneState = results[Manifest.permission.READ_PHONE_STATE] == true
        val okNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }
        val okStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        val okMic = results[Manifest.permission.RECORD_AUDIO] == true

        if (okPhoneState && okNotifications && okStorage && okMic) {
            startProtecTalkService()
        } else {
            Snackbar.make(
                binding.root,
                "Essential permissions (phone state, storage, notifications, microphone) are required. Please grant them to use ProtecTalk.",
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction("Grant") {
                    handlePermissionDenial()
                }
                .show()
        }
    }

    private val transcriptReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(ctx: Context, intent: Intent) {
            val transcript = intent.getStringExtra(ProtecTalkService.EXTRA_TRANSCRIPT) ?: ""
            val score = intent.getIntExtra(ProtecTalkService.EXTRA_SCORE, 0)
            val analysis = intent.getStringArrayListExtra(ProtecTalkService.EXTRA_ANALYSIS)?.joinToString("\n") ?: ""

            binding.tvTranscript.text = transcript
            binding.tvScore.text = "$score % scam-risk"
            binding.tvAnalysis.text = analysis

            if (score >= ProtecTalkService.ALERT_THRESHOLD) {
                binding.tvScore.setTextColor(Color.RED)
            } else {
                binding.tvScore.setTextColor(Color.WHITE)
            }
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) Check permissions and either request them or start the service
        checkPermissionsAndStartServiceIfGranted()

        // 2) Prompt user to enable Samsung’s “Auto record unknown callers”
        promptEnableCallRecording()

        // 3) Register for transcript broadcasts
        val filter = IntentFilter(ProtecTalkService.ACTION_TRANSCRIPT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(transcriptReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(transcriptReceiver, filter)
        }

        // 4) FAB to restart service - now also checks permissions
        binding.fabStart.setOnClickListener {
            if (hasRequiredPermissions()) {
                startProtecTalkService()
            } else {
                Snackbar.make(binding.root, "Permissions missing. Please grant them to restart service.", Snackbar.LENGTH_LONG).show()
                requestPermissions()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(transcriptReceiver)
    }

    private fun hasRequiredPermissions(): Boolean {
        val phoneStateGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        return phoneStateGranted && notificationsGranted && storageGranted && micGranted
    }

    private fun checkPermissionsAndStartServiceIfGranted() {
        if (hasRequiredPermissions()) {
            startProtecTalkService()
        } else {
            requestPermissions()
        }
    }

    private fun startProtecTalkService() {
        startService(Intent(this, ProtecTalkService::class.java))
        Snackbar.make(binding.root, "ProtecTalk Service started", Snackbar.LENGTH_SHORT).show()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
            perms += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        // Always request microphone permission!
        perms += Manifest.permission.RECORD_AUDIO

        permLauncher.launch(perms.toTypedArray())
    }

    private fun handlePermissionDenial() {
        val showRationalePhone = shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)
        val showRationaleNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }
        val showRationaleStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val showRationaleMic = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)

        if (showRationalePhone || showRationaleNotifications || showRationaleStorage || showRationaleMic) {
            requestPermissions()
        } else {
            Snackbar.make(
                binding.root,
                "Permissions are permanently denied. Please enable them in app settings.",
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction("Settings") {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                .show()
        }
    }

    private fun promptEnableCallRecording() {
        Snackbar.make(
            binding.root,
            "Enable Auto-record for unknown callers in Phone → Settings → Record calls",
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction("Open Settings") {
                openSamsungCallRecordingSettings()
            }
            .show()
    }

    private fun openSamsungCallRecordingSettings() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(
                    "com.samsung.android.dialer",
                    "com.samsung.android.dialer.settings.RecordingSettingsActivity",
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:com.samsung.android.dialer".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(fallback)
        }
    }
}
