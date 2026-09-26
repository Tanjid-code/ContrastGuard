package com.tanjid.contrastguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tanjid.contrastguard.service.GuardService

class PackageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_ADDED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            val packageName = intent.data?.schemeSpecificPart ?: return

            // Only scan if GuardService is running
            val prefs = context.getSharedPreferences("contrastguard_prefs", Context.MODE_PRIVATE)
            val protectionEnabled = prefs.getBoolean("protection_enabled", false)

            if (protectionEnabled) {
                val serviceIntent = Intent(context, GuardService::class.java).apply {
                    action = GuardService.ACTION_SCAN_PACKAGE
                    putExtra(GuardService.EXTRA_PACKAGE_NAME, packageName)
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}