package com.tanjid.contrastguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tanjid.contrastguard.database.AppDatabase
import com.tanjid.contrastguard.databinding.ActivityMainBinding
import com.tanjid.contrastguard.ml.OnDevicePredictor
import com.tanjid.contrastguard.service.GuardService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase

    private val pickApkLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val intent = Intent(this, ScanActivity::class.java).apply { data = it }
            startActivity(intent)
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val intent = Intent(this, FileScanActivity::class.java).apply {
                putExtra("folder_uri", it.toString())
            }
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)

        setupClickListeners()
        setupProtectionToggle()
        initAiEngine()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
        refreshProtectionStatus()
    }

    private fun setupClickListeners() {
        binding.btnScanApk.setOnClickListener {
            pickApkLauncher.launch("application/vnd.android.package-archive")
        }
        binding.btnScanInstalled.setOnClickListener {
            startActivity(Intent(this, InstalledAppsActivity::class.java))
        }
        binding.btnBatchScan.setOnClickListener {
            startActivity(Intent(this, BatchScanActivity::class.java))
        }
        binding.btnScanFiles.setOnClickListener {
            startActivity(Intent(this, FileScanActivity::class.java))
        }
        binding.btnScanFolder.setOnClickListener {
            try {
                folderPickerLauncher.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupProtectionToggle() {
        val prefs = getSharedPreferences("contrastguard_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("protection_enabled", false)
        binding.switchProtection.isChecked = isEnabled
        updateProtectionUI(isEnabled)

        binding.switchProtection.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("protection_enabled", checked).apply()
            updateProtectionUI(checked)

            if (checked) {
                val serviceIntent = Intent(this, GuardService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                stopService(Intent(this, GuardService::class.java))
            }
        }
    }

    private fun updateProtectionUI(active: Boolean) {
        if (active) {
            binding.cardProtection.setBackgroundResource(R.drawable.bg_protection_active)
            binding.tvProtectionStatus.text = "Protection Active"
            binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.safe))
        } else {
            binding.cardProtection.setBackgroundResource(R.drawable.bg_protection_inactive)
            binding.tvProtectionStatus.text = "Protection Inactive"
            binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }
    }

    private fun refreshProtectionStatus() {
        val prefs = getSharedPreferences("contrastguard_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("protection_enabled", false)
        binding.switchProtection.isChecked = isEnabled
        updateProtectionUI(isEnabled)
    }

    private fun initAiEngine() {
        lifecycleScope.launch(Dispatchers.IO) {
            val predictor = OnDevicePredictor(applicationContext)
            val ready = predictor.initialize()
            withContext(Dispatchers.Main) {
                if (ready) {
                    binding.viewStatusDot.setBackgroundColor(
                        ContextCompat.getColor(this@MainActivity, R.color.safe)
                    )
                    binding.tvServerStatus.text = "AI Engine Ready"
                    binding.tvServerStatus.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.safe)
                    )
                    binding.tvServerVersion.text = "v3.0"
                } else {
                    binding.viewStatusDot.setBackgroundColor(
                        ContextCompat.getColor(this@MainActivity, R.color.danger)
                    )
                    binding.tvServerStatus.text = "Model Load Failed"
                    binding.tvServerStatus.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.danger)
                    )
                }
            }
        }
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val total = withContext(Dispatchers.IO) { db.scanHistoryDao().getTotalScans() }
                val threats = withContext(Dispatchers.IO) { db.scanHistoryDao().getThreatCount() }
                val safe = withContext(Dispatchers.IO) { db.scanHistoryDao().getSafeCount() }
                binding.tvTotalScans.text = total.toString()
                binding.tvThreatsFound.text = threats.toString()
                binding.tvSafeApps.text = safe.toString()
            } catch (_: Exception) {}
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        val storagePerms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                storagePerms.add(Manifest.permission.READ_MEDIA_IMAGES)
                storagePerms.add(Manifest.permission.READ_MEDIA_VIDEO)
                storagePerms.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                storagePerms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (storagePerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, storagePerms.toTypedArray(), 101)
        }
    }
}