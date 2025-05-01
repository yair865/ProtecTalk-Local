package com.example.protectalk.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.example.protectalk.services.ProtectalkService
import kotlin.jvm.java

class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (stateStr == TelephonyManager.EXTRA_STATE_RINGING && incomingNumber != null) {
                if (isUnknownNumber(context, incomingNumber)) {
                    val serviceIntent = Intent(context, ProtectalkService::class.java)
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }

    private fun isUnknownNumber(context: Context, number: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(number).build()
        context.contentResolver.query(uri, null, null, null, null).use { cursor ->
            return cursor == null || cursor.count == 0
        }
    }
}
