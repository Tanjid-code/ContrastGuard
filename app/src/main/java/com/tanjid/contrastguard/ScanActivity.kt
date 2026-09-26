package com.tanjid.contrastguard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.tanjid.contrastguard.database.AppDatabase
import com.tanjid.contrastguard.database.ScanHistoryEntity
import com.tanjid.contrastguard.databinding.ActivityScanBinding
import com.tanjid.contrastguard.ml.FeatureExtractor
import com.tanjid.contrastguard.ml.OnDevicePredictor
import com.tanjid.contrastguard.ml.RuleEngine
import com.tanjid.contrastguard.model.ScanResult
import com.tanjid.contrastguard.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private var scanJob: Job? = null
    private lateinit var db: AppDatabase
    private lateinit var predictor: OnDevicePredictor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        predictor = OnDevicePredictor(applicationContext)

        binding.btnCancel.setOnClickListener {
            scanJob?.cancel()
            finish()
        }

        val uri = intent.data
        val apkPath = intent.getStringExtra("apk_path")
        val pkgName = intent.getStringExtra("package_name")
        val displayName = intent.getStringExtra("display_name")

        when {
            uri != null -> processUri(uri)
            apkPath != null -> processPath(apkPath, pkgName, displayName)
            else -> {
                Toast.makeText(this, "No file provided", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun processUri(uri: Uri) {
        val fileName = FileUtils.getFileName(this, uri) ?: "unknown.apk"
        binding.tvFileName.text = fileName
        updateProgress(5, "Reading APK file...", "Copying to secure cache...")

        scanJob = lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                FileUtils.copyUriToCache(this@ScanActivity, uri)
            }
            if (file == null) {
                showError("Failed to open APK file")
                return@launch
            }

            val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            val appName = try {
                if (info != null) {
                    info.applicationInfo?.sourceDir = file.absolutePath
                    info.applicationInfo?.publicSourceDir = file.absolutePath
                    packageManager.getApplicationLabel(info.applicationInfo!!).toString()
                } else fileName
            } catch (e: Exception) {
                fileName
            }

            binding.tvFileName.text = appName
            executeScan(file, appName, info?.packageName, isTemp = true)
        }
    }

    private fun processPath(path: String, packageName: String?, displayName: String?) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "APK not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.tvFileName.text = displayName ?: file.name
        scanJob = lifecycleScope.launch {
            executeScan(file, displayName ?: file.name, packageName, false)
        }
    }

    private suspend fun executeScan(file: File, appName: String, packageName: String?, isTemp: Boolean) {
        try {
            val startTime = System.currentTimeMillis()

            updateProgress(15, "Extracting Features", "Reading permissions & manifest...")
            val features = withContext(Dispatchers.IO) {
                FeatureExtractor.extractFeatures(this@ScanActivity, file)
            }

            updateProgress(45, "Analyzing DEX Bytecode", "Scanning ${(file.length() / 1024)} KB of code...")

            updateProgress(65, "Running Rule Engine", "Checking security rules...")
            val ruleReport = withContext(Dispatchers.IO) {
                RuleEngine.analyze(this@ScanActivity, file, packageName)
            }

            updateProgress(85, "Running AI Neural Network", "PyTorch inference on 299 features...")
            val prediction = withContext(Dispatchers.IO) {
                predictor.predict(features, ruleReport.riskScore, ruleReport.criticalHits)
            }

            updateProgress(95, "Finalizing Analysis", "Combining results...")

            val finalIsMalware = if (ruleReport.isKnownSafe) false else prediction.isMalware
            val finalLabel = if (finalIsMalware) "MALWARE" else "SAFE"
            val finalConfidence = if (ruleReport.isKnownSafe) 99f else prediction.confidence

            val threatLevel = when {
                ruleReport.isKnownSafe -> "safe"
                prediction.hybridScore > 75f -> "critical"
                prediction.hybridScore > 55f -> "high"
                prediction.hybridScore > 40f -> "medium"
                prediction.hybridScore > 25f -> "low"
                else -> "safe"
            }

            val risks = FeatureExtractor.getRiskIndicators(features)
            val explanation = FeatureExtractor.generateExplanation(
                finalIsMalware, prediction.modelScore, prediction.ruleScore,
                prediction.hybridScore, risks, features
            )

            val scanResult = ScanResult(
                appName = appName,
                packageName = packageName ?: appName,
                isMalware = finalIsMalware,
                label = finalLabel,
                confidence = finalConfidence,
                benignProbability = prediction.benignProbability,
                malwareProbability = prediction.malwareProbability,
                threatLevel = threatLevel,
                riskIndicators = risks,
                featuresDetected = features.count { it > 0.5f },
                scanTimeMs = System.currentTimeMillis() - startTime,
                fileSize = file.length(),
                fileHash = withContext(Dispatchers.IO) { FileUtils.calculateSha256(file) },
                modelScore = prediction.modelScore,
                ruleScore = prediction.ruleScore,
                hybridScore = prediction.hybridScore,
                ruleFlags = ruleReport.flags,
                explanation = explanation,
                verdictSource = prediction.verdictSource
            )

            withContext(Dispatchers.IO) {
                db.scanHistoryDao().insertScan(
                    ScanHistoryEntity(
                        appName = appName,
                        packageName = packageName ?: appName,
                        prediction = finalLabel,
                        confidence = finalConfidence,
                        malwareProbability = prediction.malwareProbability,
                        benignProbability = prediction.benignProbability,
                        threatLevel = threatLevel,
                        riskIndicatorsJson = Gson().toJson(risks),
                        scanTimeMs = scanResult.scanTimeMs,
                        fileHash = scanResult.fileHash,
                        fileSize = file.length(),
                        featuresDetected = scanResult.featuresDetected
                    )
                )
            }

            updateProgress(100, "Complete", "Opening results...")

            startActivity(Intent(this@ScanActivity, ScanResultActivity::class.java).apply {
                putExtra("result_json", Gson().toJson(scanResult))
            })
            finish()

        } catch (e: Exception) {
            e.printStackTrace()
            showError("Scan failed: ${e.localizedMessage}")
        } finally {
            if (isTemp) try { file.delete() } catch (_: Exception) {}
        }
    }

    private fun updateProgress(percent: Int, status: String, detail: String) {
        runOnUiThread {
            binding.progressScan.progress = percent
            binding.tvProgressPercent.text = "$percent%"
            binding.tvScanStatus.text = status
            binding.tvScanDetail.text = detail
        }
    }

    private fun showError(msg: String) {
        binding.tvScanStatus.text = "Scan Failed"
        binding.tvScanStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
        binding.tvScanDetail.text = msg
        binding.btnCancel.text = "Close"
    }

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
    }
}