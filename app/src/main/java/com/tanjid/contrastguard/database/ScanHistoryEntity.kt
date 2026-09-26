package com.tanjid.contrastguard.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appName: String,
    val packageName: String,
    val prediction: String,
    val confidence: Float,
    val malwareProbability: Float,
    val benignProbability: Float,
    val threatLevel: String,
    val riskIndicatorsJson: String,
    val scanTimeMs: Long,
    val fileHash: String,
    val fileSize: Long,
    val featuresDetected: Int,
    val timestamp: Long = System.currentTimeMillis()
)