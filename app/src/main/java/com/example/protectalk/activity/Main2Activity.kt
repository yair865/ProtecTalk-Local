package com.example.protectalk.activity

import android.Manifest
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.protectalk.databinding.ActivityMain2Binding
import com.example.protectalk.services.ProtecTalkService
import com.google.android.material.snackbar.Snackbar

class Main2Activity : AppCompatActivity() {
    private lateinit var binding: ActivityMain2Binding

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val okPhone = results[Manifest.permission.READ_PHONE_STATE] == true
        val okStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (!okPhone || !okStorage) {
            Snackbar.make(binding.root, "Permissions required for transcription", Snackbar.LENGTH_INDEFINITE)
                .setAction("Grant") { requestPermissions() }
                .show()
        }
    }

    private val transcriptReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val transcript = intent.getStringExtra(ProtecTalkService.EXTRA_TRANSCRIPT) ?: ""
            val score      = intent.getIntExtra   (ProtecTalkService.EXTRA_SCORE, 0)
            val analysis   = intent.getStringExtra(ProtecTalkService.EXTRA_ANALYSIS) ?: ""

            binding.tvTranscript.text = transcript
            binding.tvScore.text      = "$score % scam-risk"
            binding.tvAnalysis.text   = analysis

            // highlight if above threshold
            if (score >= ProtecTalkService.ALERT_THRESHOLD) {
                binding.tvScore.setTextColor(Color.RED)
            } else {
                binding.tvScore.setTextColor(Color.WHITE)
            }
        }
    }

    companion object {
        private const val REQUEST_ROLE_DIALER = 2001
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()
        promptDefaultDialer()

        // start service
        ContextCompat.startForegroundService(
            this,
            Intent(this, ProtecTalkService::class.java)
        )
        Snackbar.make(binding.root, "ProtecTalk Service started", Snackbar.LENGTH_LONG).show()

        // register for results
        val filter = IntentFilter(ProtecTalkService.ACTION_TRANSCRIPT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(transcriptReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(transcriptReceiver, filter)
        }

        binding.fabStart.setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, ProtecTalkService::class.java)
            )
            Snackbar.make(binding.root, "Service restarted", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permLauncher.launch(perms.toTypedArray())
    }

    private fun promptDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleMgr = getSystemService(RoleManager::class.java)
            if (roleMgr != null && !roleMgr.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(
                    roleMgr.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    REQUEST_ROLE_DIALER
                )
            }
        } else {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            startActivityForResult(
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                },
                REQUEST_ROLE_DIALER
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ROLE_DIALER) {
            val msg = if (resultCode == RESULT_OK) "Set as default dialer"
            else "Dialer role not granted"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(transcriptReceiver)
    }
}
