package com.example.prankcallapp.broadcastreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.prankcallapp.serviceclass.FakeCallService

class FakeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val serviceIntent = Intent(context, FakeCallService::class.java).apply {
            putExtra("CALLER_NAME", intent?.getStringExtra("CALLER_NAME"))
            putExtra("CALLER_NUMBER", intent?.getStringExtra("CALLER_NUMBER"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(serviceIntent)
        } else {
            context?.startService(serviceIntent)
        }
    }
}
