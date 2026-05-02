package com.mt.organizemessages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle incoming SMS (system already writes it to DB if we are the default app)
    }
}
