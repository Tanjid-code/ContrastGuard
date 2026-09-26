package com.tanjid.contrastguard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tanjid.contrastguard.MainActivity
import com.tanjid.contrastguard.R
import com.tanjid.contrastguard.ml.FeatureExtractor
import com.tanjid.contrastguard.ml.OnDevicePredictor
import com.tanjid.contrastguard.ml.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object LaunchInterceptor {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scannedPackages = mutableSetOf<String>()

    fun checkAppOnLaunch(context: Context, packageName: String) {
        // Skip if already scanned this session
        if (scannedPackages.contains(packageName)) return
        scannedPackages.add(packageName)

        // Skip system apps and trusted packages
        val trustedPrefixes = listOf(
            "com.google.", "com.android.", "com.samsung.",
            "com.sec.", "com.miui.", "com.xiaomi.",
            "com.oneplus.", "com.oppo.", "com.vivo.",
            "com.tanjid.contrastguard"
        )
        if (trustedPrefixes.any { packageName.startsWith(it) }) return

        scope.launch {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val apkFile = File(appInfo.sourceDir ?: return@launch)
                if (!apkFile.exists()) return@launch

                val features = FeatureExtractor.extractFeatures(context, apkFile)
                val ruleReport = RuleEngine.analyze(context, apkFile, packageName)
                val predictor = OnDevicePredictor(context)
                val prediction = predictor.predict(features, ruleReport.riskScore, ruleReport.criticalHits)

                val isMalware = if (ruleReport.isKnownSafe) false else prediction.isMalware

                if (isMalware) {
                    showLaunchWarning(context, packageName, prediction.confidence)
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    private fun showLaunchWarning(context: Context, packageName: String, confidence: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "launch_warning", "App Launch Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, "launch_warning")
            .setSmallIcon(R.drawable.ic_danger)
            .setContentTitle("Warning: Suspicious App Opened")
            .setContentText("$appName may be malicious (${String.format("%.0f", confidence)}% threat)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$appName has been flagged as potentially malicious. " +
                        "Consider uninstalling it immediately. " +
                        "Confidence: ${String.format("%.1f", confidence)}%"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(context.getColor(R.color.danger))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(packageName.hashCode(), notification)
    }
}