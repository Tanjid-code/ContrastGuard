package com.tanjid.contrastguard

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.tanjid.contrastguard.adapter.RiskAdapter
import com.tanjid.contrastguard.databinding.ActivityScanResultBinding
import com.tanjid.contrastguard.databinding.DetailRowBinding
import com.tanjid.contrastguard.model.ScanResult
import com.tanjid.contrastguard.util.FileUtils

class ScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra("result_json") ?: return finish()
        val result = Gson().fromJson(json, ScanResult::class.java)
        displayResult(result)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnScanAgain.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }

    private fun displayResult(result: ScanResult) {
        val isMalware = result.isMalware

        binding.tvPrediction.text = result.label
        binding.tvPrediction.setTextColor(ContextCompat.getColor(this, if (isMalware) R.color.danger else R.color.safe))
        binding.ivResultIcon.setImageResource(if (isMalware) R.drawable.ic_danger else R.drawable.ic_check)

        val (threatText, threatBg, threatColor) = when (result.threatLevel) {
            "critical" -> Triple("CRITICAL THREAT", R.drawable.bg_status_danger, R.color.critical)
            "high" -> Triple("HIGH RISK", R.drawable.bg_status_danger, R.color.danger)
            "medium" -> Triple("MEDIUM RISK", R.drawable.bg_status_warning, R.color.warning)
            "low" -> Triple("LOW RISK", R.drawable.bg_status_warning, R.color.warning)
            else -> Triple("NO THREAT DETECTED", R.drawable.bg_status_safe, R.color.safe)
        }
        binding.tvThreatLevel.text = threatText
        binding.tvThreatLevel.setBackgroundResource(threatBg)
        binding.tvThreatLevel.setTextColor(ContextCompat.getColor(this, threatColor))
        binding.tvResultFilename.text = result.appName
        binding.tvConfidence.text = String.format("%.1f%%", result.confidence)

        binding.progressModel.progress = result.modelScore.toInt()
        binding.tvModelScore.text = String.format("%.1f%% malware risk", result.modelScore)
        binding.progressRule.progress = result.ruleScore.toInt()
        binding.tvRuleScore.text = String.format("%.1f%% dangerous patterns", result.ruleScore)
        binding.progressHybrid.progress = result.hybridScore.toInt()
        binding.tvHybridScore.text = String.format("%.1f%% overall threat", result.hybridScore)

        // Show decision verdict source
        if (result.verdictSource.isNotEmpty()) {
            binding.tvVerdictSource.visibility = View.VISIBLE
            binding.tvVerdictSource.text = "Decision Engine: ${result.verdictSource}"
        } else {
            binding.tvVerdictSource.visibility = View.GONE
        }

        // Show human-readable analysis explanation
        if (result.explanation.isNotEmpty()) {
            binding.cardFlags.visibility = View.VISIBLE
            binding.tvRuleFlags.text = result.explanation
        } else {
            binding.cardFlags.visibility = View.GONE
        }

        if (result.riskIndicators.isNotEmpty()) {
            binding.cardRisks.visibility = View.VISIBLE
            binding.tvCriticalCount.text = "${result.riskIndicators.count { it.severity == "critical" }} Critical"
            binding.tvHighCount.text = "${result.riskIndicators.count { it.severity == "high" }} High"
            binding.tvMediumCount.text = "${result.riskIndicators.count { it.severity == "medium" }} Medium"
            binding.rvRisks.layoutManager = LinearLayoutManager(this)
            binding.rvRisks.adapter = RiskAdapter(result.riskIndicators)
        } else {
            binding.cardRisks.visibility = View.GONE
        }

        setupRow(binding.rowScanTime, "Scan Duration", "${result.scanTimeMs} ms")
        setupRow(binding.rowFeatures, "Active Features", "${result.featuresDetected} / 299")
        setupRow(binding.rowFileSize, "File Size", FileUtils.formatFileSize(result.fileSize))
        setupRow(binding.rowFileHash, "SHA-256", result.fileHash.take(16) + "...")
    }

    private fun setupRow(rowBinding: DetailRowBinding, label: String, value: String) {
        rowBinding.tvLabel.text = label
        rowBinding.tvValue.text = value
    }
}