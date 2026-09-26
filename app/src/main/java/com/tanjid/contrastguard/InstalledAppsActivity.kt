package com.tanjid.contrastguard

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tanjid.contrastguard.adapter.InstalledAppAdapter
import com.tanjid.contrastguard.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstalledAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackHistory.setOnClickListener { finish() }
        binding.btnClearHistory.visibility = View.GONE

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            }

            if (apps.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                binding.rvHistory.layoutManager = LinearLayoutManager(this@InstalledAppsActivity)
                binding.rvHistory.adapter = InstalledAppAdapter(
                    apps = apps,
                    packageManager = packageManager,
                    onScanClick = { appInfo ->
                        val path = appInfo.sourceDir
                        val displayName = packageManager.getApplicationLabel(appInfo).toString()
                        if (path != null) {
                            val intent = Intent(this@InstalledAppsActivity, ScanActivity::class.java).apply {
                                putExtra("apk_path", path)
                                putExtra("package_name", appInfo.packageName)
                                putExtra("display_name", displayName)
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@InstalledAppsActivity, "Cannot access APK", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}