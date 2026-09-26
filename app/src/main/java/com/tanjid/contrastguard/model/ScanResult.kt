package com.tanjid.contrastguard.model

data class ScanResult(
    val appName: String,
    val packageName: String,
    val isMalware: Boolean,
    val label: String,
    val confidence: Float,
    val benignProbability: Float,
    val malwareProbability: Float,
    val threatLevel: String,
    val riskIndicators: List<RiskIndicator>,
    val featuresDetected: Int,
    val totalFeatures: Int = 299,
    val scanTimeMs: Long,
    val fileSize: Long,
    val fileHash: String,
    val modelScore: Float = 0f,
    val ruleScore: Float = 0f,
    val hybridScore: Float = 0f,
    val ruleFlags: List<String> = emptyList(),
    val explanation: String = "",
    val verdictSource: String = ""
)