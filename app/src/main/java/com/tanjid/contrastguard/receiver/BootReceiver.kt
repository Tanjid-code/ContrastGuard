package com.tanjid.contrastguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tanjid.contrastguard.service.GuardService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("contrastguard_prefs", Context.MODE_PRIVATE)
            val protectionEnabled = prefs.getBoolean("protection_enabled", false)

            if (protectionEnabled) {
                val serviceIntent = Intent(context, GuardService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}