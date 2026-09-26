package com.tanjid.contrastguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tanjid.contrastguard.MainActivity
import com.tanjid.contrastguard.R
import com.tanjid.contrastguard.database.AppDatabase
import com.tanjid.contrastguard.database.ScanHistoryEntity
import com.tanjid.contrastguard.ml.DexReader
import com.tanjid.contrastguard.ml.FeatureExtractor
import com.tanjid.contrastguard.ml.OnDevicePredictor
import com.tanjid.contrastguard.ml.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class BatchScanService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var scanJob: Job? = null

    companion object {
        const val CHANNEL_ID = "batch_scan_progress"
        const val RESULT_CHANNEL_ID = "batch_scan_result"
        const val NOTIFICATION_ID = 5001
        const val RESULT_NOTIFICATION_ID = 5002

        const val ACTION_START = "com.tanjid.contrastguard.START_BATCH"
        const val ACTION_STOP = "com.tanjid.contrastguard.STOP_BATCH"

        const val BROADCAST_PROGRESS = "com.tanjid.contrastguard.BATCH_PROGRESS"
        const val EXTRA_CURRENT = "current"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_THREATS = "threats"
        const val EXTRA_DONE = "done"
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildProgressNotification(0, 0, "Starting..."))
                startScan()
            }
            ACTION_STOP -> {
                scanJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun startScan() {
        scanJob = scope.launch {
            try {
                val pm = packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

                val total = apps.size
                if (total == 0) {
                    showResultNotification(0, 0, emptyList())
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                val predictor = OnDevicePredictor(applicationContext)
                predictor.initialize()
                val db = AppDatabase.getDatabase(applicationContext)

                var threatCount = 0
                var scanned = 0
                val threatNames = mutableListOf<String>()

                for (app in apps) {
                    if (scanJob?.isCancelled == true) break

                    val appName = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (e: Exception) {
                        app.packageName
                    }

                    scanned++
                    updateProgressNotification(scanned, total, appName)

                    val progressIntent = Intent(BROADCAST_PROGRESS).apply {
                        setPackage(packageName)
                        putExtra(EXTRA_CURRENT, scanned)
                        putExtra(EXTRA_TOTAL, total)
                        putExtra(EXTRA_APP_NAME, appName)
                        putExtra(EXTRA_THREATS, threatCount)
                        putExtra(EXTRA_DONE, false)
                    }
                    sendBroadcast(progressIntent)

                    try {
                        val apkPath = app.sourceDir
                        if (apkPath == null) continue

                        val apkFile = File(apkPath)
                        if (!apkFile.exists() || !apkFile.canRead()) continue

                        // Read DEX bytes ONCE, share with both engines.
                        // This is the fix: previously FeatureExtractor and
                        // RuleEngine each independently re-opened the zip and
                        // built their own full String copy of the DEX content —
                        // doubling I/O and doubling peak memory per app.
                        val dexBuffers = try {
                            DexReader.readAllDexBytes(apkFile)
                        } catch (e: Exception) {
                            continue
                        }

                        val features = try {
                            FeatureExtractor.extractFeatures(applicationContext, apkFile, dexBuffers)
                        } catch (e: Exception) {
                            continue
                        }

                        val ruleReport = try {
                            RuleEngine.analyze(applicationContext, apkFile, app.packageName, dexBuffers)
                        } catch (e: Exception) {
                            continue
                        }

                        val prediction = try {
                            predictor.predict(features, ruleReport.riskScore, ruleReport.criticalHits)
                        } catch (e: Exception) {
                            continue
                        }

                        val isMalware = if (ruleReport.isKnownSafe) false else prediction.isMalware

                        if (isMalware) {
                            threatCount++
                            threatNames.add(appName)
                        }

                        try {
                            db.scanHistoryDao().insertScan(
                                ScanHistoryEntity(
                                    appName = appName,
                                    packageName = app.packageName,
                                    prediction = if (isMalware) "MALWARE" else "SAFE",
                                    confidence = prediction.confidence,
                                    malwareProbability = prediction.malwareProbability,
                                    benignProbability = prediction.benignProbability,
                                    threatLevel = if (isMalware) "high" else "safe",
                                    riskIndicatorsJson = "[]",
                                    scanTimeMs = 0,
                                    fileHash = "",
                                    fileSize = apkFile.length(),
                                    featuresDetected = features.count { it > 0.5f }
                                )
                            )
                        } catch (e: Exception) {
                            // DB error, continue
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                showResultNotification(scanned, threatCount, threatNames)

                val doneIntent = Intent(BROADCAST_PROGRESS).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_CURRENT, scanned)
                    putExtra(EXTRA_TOTAL, total)
                    putExtra(EXTRA_THREATS, threatCount)
                    putExtra(EXTRA_DONE, true)
                }
                sendBroadcast(doneIntent)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progress = NotificationChannel(
                CHANNEL_ID,
                "Batch Scan Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val result = NotificationChannel(
                RESULT_CHANNEL_ID,
                "Batch Scan Results",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(progress)
            nm.createNotificationChannel(result)
        }
    }

    private fun buildProgressNotification(current: Int, total: Int, appName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BatchScanService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val percent = if (total > 0) (current * 100 / total) else 0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scanning Apps... ($percent%)")
            .setContentText("$current / $total  •  $appName")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(total.coerceAtLeast(1), current, total == 0)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun updateProgressNotification(current: Int, total: Int, appName: String) {
        val notification = buildProgressNotification(current, total, appName)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showResultNotification(scanned: Int, threats: Int, threatNames: List<String>) {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (threats == 0) "Scan Complete — All Clean" else "Scan Complete — $threats Threats Found"
        val text = if (threats == 0) {
            "All $scanned apps are safe."
        } else {
            "$threats threats: ${threatNames.take(3).joinToString(", ")}${if (threatNames.size > 3) "..." else ""}"
        }

        val bigText = if (threats == 0) {
            "Scanned $scanned apps. No malware detected."
        } else {
            "Scanned $scanned apps.\n\nThreats found:\n" +
                    threatNames.joinToString("\n") { "• $it" }
        }

        val notification = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(getColor(if (threats > 0) R.color.danger else R.color.safe))
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(RESULT_NOTIFICATION_ID, notification)
    }
}