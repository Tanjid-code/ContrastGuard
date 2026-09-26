package com.tanjid.contrastguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.tanjid.contrastguard.MainActivity
import com.tanjid.contrastguard.R
import com.tanjid.contrastguard.database.AppDatabase
import com.tanjid.contrastguard.database.ScanHistoryEntity
import com.tanjid.contrastguard.ml.FeatureExtractor
import com.tanjid.contrastguard.ml.OnDevicePredictor
import com.tanjid.contrastguard.ml.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class GuardService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val predictor by lazy { OnDevicePredictor(applicationContext) }
    private val initMutex = Mutex()
    private var isModelReady = false

    companion object {
        const val CHANNEL_ID = "contrastguard_guard"
        const val NOTIFICATION_ID = 1001
        const val THREAT_CHANNEL_ID = "contrastguard_threats"
        const val ACTION_SCAN_PACKAGE = "com.tanjid.contrastguard.SCAN_PACKAGE"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    override fun onCreate() {
        super.onCreate()
        // Only cheap synchronous work here. No file I/O, no model loading.
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be the first thing that happens, every time, with zero
        // dependency on anything that can block. This is what fixes
        // ForegroundServiceDidNotStartInTimeException risk.
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        if (intent?.action == ACTION_SCAN_PACKAGE) {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            if (packageName != null) {
                scanPackage(packageName)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private suspend fun ensureModelReady(): Boolean {
        if (isModelReady) return true
        initMutex.withLock {
            if (isModelReady) return true
            isModelReady = predictor.initialize()
        }
        return isModelReady
    }

    private fun scanPackage(packageName: String) {
        scope.launch {
            try {
                if (!ensureModelReady()) return@launch

                val pm = packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val apkPath = appInfo.sourceDir ?: return@launch
                val apkFile = File(apkPath)
                if (!apkFile.exists()) return@launch

                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }

                val startTime = System.currentTimeMillis()

                val features = FeatureExtractor.extractFeatures(applicationContext, apkFile)
                val ruleReport = RuleEngine.analyze(applicationContext, apkFile, packageName)
                val prediction = predictor.predict(features, ruleReport.riskScore, ruleReport.criticalHits)
                val scanTime = System.currentTimeMillis() - startTime

                val finalMalware = if (ruleReport.isKnownSafe) false else prediction.isMalware
                val finalLabel = if (finalMalware) "MALWARE" else "SAFE"

                val db = AppDatabase.getDatabase(applicationContext)
                db.scanHistoryDao().insertScan(
                    ScanHistoryEntity(
                        appName = appName,
                        packageName = packageName,
                        prediction = finalLabel,
                        confidence = prediction.confidence,
                        malwareProbability = prediction.malwareProbability,
                        benignProbability = prediction.benignProbability,
                        threatLevel = if (finalMalware) "high" else "safe",
                        riskIndicatorsJson = Gson().toJson(FeatureExtractor.getRiskIndicators(features)),
                        scanTimeMs = scanTime,
                        fileHash = "",
                        fileSize = apkFile.length(),
                        featuresDetected = features.count { it > 0.5f }
                    )
                )

                if (finalMalware) {
                    showThreatNotification(appName, packageName, prediction.malwareProbability)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val guardChannel = NotificationChannel(
                CHANNEL_ID,
                "Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing protection monitoring"
                setShowBadge(false)
            }

            val threatChannel = NotificationChannel(
                THREAT_CHANNEL_ID,
                "Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Malware threat notifications"
                enableVibration(true)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(guardChannel)
            nm.createNotificationChannel(threatChannel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ContrastGuard Active")
            .setContentText("Monitoring your device for threats")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun showThreatNotification(appName: String, packageName: String, probability: Float) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, THREAT_CHANNEL_ID)
            .setContentTitle("Threat Detected!")
            .setContentText("$appName may be malware (${String.format("%.1f", probability)}% confidence)")
            .setSmallIcon(R.drawable.ic_danger)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(getColor(R.color.danger))
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(packageName.hashCode(), notification)
    }
}