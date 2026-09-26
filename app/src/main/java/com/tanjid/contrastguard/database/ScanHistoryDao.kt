package com.tanjid.contrastguard.database

import androidx.room.*

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    suspend fun getAllScans(): List<ScanHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistoryEntity)

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun getTotalScans(): Int

    @Query("SELECT COUNT(*) FROM scan_history WHERE prediction = 'MALWARE'")
    suspend fun getThreatCount(): Int

    @Query("SELECT COUNT(*) FROM scan_history WHERE prediction = 'BENIGN'")
    suspend fun getSafeCount(): Int
}