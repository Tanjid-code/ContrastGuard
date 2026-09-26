package com.tanjid.contrastguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tanjid.contrastguard.databinding.ActivityBatchScanBinding
import com.tanjid.contrastguard.service.BatchScanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchScanBinding
    private var totalApps = 0

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BatchScanService.BROADCAST_PROGRESS) return

            val current = intent.getIntExtra(BatchScanService.EXTRA_CURRENT, 0)
            val total = intent.getIntExtra(BatchScanService.EXTRA_TOTAL, 0)
            val appName = intent.getStringExtra(BatchScanService.EXTRA_APP_NAME) ?: ""
            val threats = intent.getIntExtra(BatchScanService.EXTRA_THREATS, 0)
            val done = intent.getBooleanExtra(BatchScanService.EXTRA_DONE, false)

            if (total > 0) {
                val percent = current * 100 / total
                binding.progressBatch.max = 100
                binding.progressBatch.progress = percent
                binding.tvProgressPercent.text = "$percent%"
                binding.tvProgressCount.text = "$current / $total apps"
                binding.tvCurrentApp.text = appName
                binding.tvThreatsFound.text = "$threats threats"
            }

            if (done) {
                showResults(current, threats)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnStartScan.setOnClickListener {
            startBatchScan()
        }

        binding.btnStopScan.setOnClickListener {
            stopBatchScan()
        }

        binding.btnFinish.setOnClickListener {
            finish()
        }

        loadAppCount()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(BatchScanService.BROADCAST_PROGRESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(progressReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(progressReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    private fun loadAppCount() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .count { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            }
            totalApps = count
            binding.tvAppCount.text = "$count apps will be scanned"
        }
    }

    private fun startBatchScan() {
        binding.layoutStart.visibility = View.GONE
        binding.layoutProgress.visibility = View.VISIBLE
        binding.layoutResults.visibility = View.GONE

        binding.progressBatch.progress = 0
        binding.tvProgressPercent.text = "0%"
        binding.tvProgressCount.text = "0 / $totalApps apps"
        binding.tvCurrentApp.text = "Initializing..."
        binding.tvThreatsFound.text = "0 threats"

        val intent = Intent(this, BatchScanService::class.java).apply {
            action = BatchScanService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)

        Toast.makeText(
            this,
            "Scan started. You can minimize the app — we'll notify you when done.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun stopBatchScan() {
        val intent = Intent(this, BatchScanService::class.java).apply {
            action = BatchScanService.ACTION_STOP
        }
        startService(intent)

        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showResults(scanned: Int, threats: Int) {
        binding.layoutProgress.visibility = View.GONE
        binding.layoutResults.visibility = View.VISIBLE

        binding.tvResultScanned.text = scanned.toString()
        binding.tvResultThreats.text = threats.toString()
        binding.tvResultSafe.text = (scanned - threats).toString()

        if (threats == 0) {
            binding.tvResultTitle.text = "All Clean"
            binding.tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.safe))
            binding.tvResultDetail.text = "Scanned $scanned apps. No threats detected."
        } else {
            binding.tvResultTitle.text = "$threats Threats Found"
            binding.tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
            binding.tvResultDetail.text = "Open scan history to see details."
        }
    }
}