package com.tanjid.contrastguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.tanjid.contrastguard.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back button
        binding.btnBackSettings.setOnClickListener {
            finish()
        }

        // Battery optimization button
        binding.btnBatteryOptimization.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Check whether battery optimization is already disabled for this app
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {

            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = Uri.parse("package:$packageName")
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback for devices/OEMs that don't support the direct request
                startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            }
        }
    }
}