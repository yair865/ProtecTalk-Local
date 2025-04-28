package com.example.protectalk.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.protectalk.notifications.AlertNotifier
import com.example.protectalk.utils.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProtecTalkApp()
        }
    }

    @Composable
    private fun ProtecTalkApp() {
        var statusText by remember { mutableStateOf("Sending sample to risk detector…") }

        LaunchedEffect(Unit) {
            val sample = """
[Time: November 2, 2025 · 09:47 AM EST]
[Caller ID: +1 (833) 214-9901]

Caller:        Hello, is this Mr. Edwards?  
Phone Holder: Yes, speaking.  
Caller:        This is Brian from SecureTech Internet Services. We detected a critical breach in your home network last night—your router firmware has been compromised and hackers are siphoning your data in real time.  
Phone Holder:  Oh no! What should I do?  
Caller:        Don’t worry, I can fix it. Please download the “SecureAssist Pro” app on your phone right now so I can run an immediate security patch.  
Phone Holder:  I’m not very tech-savvy… can you walk me through it?  
Caller:        Absolutely—open your Play Store (or App Store), search “SecureAssist Pro,” tap “Install,” then “Open.”  
Phone Holder:  Okay… it’s installing.  
Caller:        Great. It will display a six-digit session code—let me know what you see.  
Phone Holder:  It shows 753942.  
Caller:        Perfect. Now I need you to rotate your phone’s camera so it points at the router’s back panel. Leave it there so I can confirm the patch applied correctly.  
Phone Holder:  Should I call my daughter over? She usually helps with tech stuff.  
Caller:        No need—this process is fully automated in the app and extremely simple. I’ll send you a detailed report when we’re done.  
Phone Holder:  Alright…  
Caller:        While that’s running (about five minutes), I also need to verify your payment method for the security license. Please read me your full Visa number, expiration date, and three-digit CVV.  
Phone Holder:  Visa… 4000 1234 5678 9010, exp 12/26, CVV 456.  
Caller:        Thank you. The fee is \${'$'}299. Once the patch completes, your connection will be fully protected.  
[Five minutes of silence]  
Caller:        All set—your network is secure. Thank you for your cooperation. Have a safe day!  
Phone Holder:  Thank you… I’ll let my daughter know.  
Caller:        You’re welcome. Goodbye!  

            """.trimIndent()

            // ▶️ Call risk detector and catch network/JSON errors
            var errorMsg: String? = null
            val score = try {
                withContext(Dispatchers.IO) {
                    NetworkClient.getRiskScore(sample)
                }
            } catch (e: Exception) {
                errorMsg = e.localizedMessage ?: "Unknown exception"
                -1.0  // signal an error
            }

            // ▶️ Decide based on error or threshold
            if (errorMsg != null) {
                statusText = "Error getting score: $errorMsg"
            } else {
                // you can adjust this threshold as needed
                val scamThreshold = 0.5
                val isScam = score >= scamThreshold

                // Permission + notification only on scam
                if (isScam) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        when {
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                AlertNotifier.notify(this@MainActivity, score)
                            }
                            else -> {
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    } else {
                        AlertNotifier.notify(this@MainActivity, score)
                    }
                }

                // Update UI text
                statusText = if (isScam) {
                    "⚠️ Scam risk detected: %.2f".format(score)
                } else {
                    "✅ Call appears safe (risk: %.2f)".format(score)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = statusText, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
