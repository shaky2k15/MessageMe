package com.mt.organizemessages

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle headless SMS sending (e.g., from Android Auto)
        return START_NOT_STICKY
    }
}
