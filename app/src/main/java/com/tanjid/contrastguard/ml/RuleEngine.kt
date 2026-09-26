package com.tanjid.contrastguard.ml

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object RuleEngine {

    private val CRITICAL_PERMS = setOf(
        "SEND_SMS", "WRITE_SMS", "RECEIVE_SMS", "READ_SMS", "BROADCAST_SMS",
        "BIND_DEVICE_ADMIN", "BIND_ACCESSIBILITY_SERVICE",
        "INSTALL_PACKAGES", "DELETE_PACKAGES", "REQUEST_INSTALL_PACKAGES",
        "MOUNT_FORMAT_FILESYSTEMS", "MASTER_CLEAR", "FACTORY_TEST",
        "WRITE_SECURE_SETTINGS", "REQUEST_SUPERUSER", "ACCESS_SUPERUSER",
        "READ_LOGS", "DUMP", "SHUTDOWN", "REBOOT",
        "FORCE_STOP_PACKAGES", "MOVE_PACKAGE", "USES_POLICY_FORCE_LOCK"
    )

    private val HIGH_RISK_PERMS = setOf(
        "RECORD_AUDIO", "CAPTURE_AUDIO_OUTPUT", "RECORD_VIDEO",
        "CALL_PHONE", "CALL_PRIVILEGED", "PROCESS_OUTGOING_CALLS",
        "PROCESS_INCOMING_CALLS", "ANSWER_PHONE", "MODIFY_PHONE_STATE",
        "SYSTEM_ALERT_WINDOW", "SYSTEM_OVERLAY_WINDOW",
        "READ_CONTACTS", "WRITE_CONTACTS",
        "READ_CALL_LOG", "WRITE_CALL_LOG",
        "READ_CALENDAR", "WRITE_CALENDAR",
        "GET_ACCOUNTS", "MANAGE_ACCOUNTS", "AUTHENTICATE_ACCOUNTS",
        "PACKAGE_USAGE_STATS", "REAL_GET_TASKS", "GET_TASKS"
    )

    private val MEDIUM_RISK_PERMS = setOf(
        "CAMERA", "ACCESS_FINE_LOCATION", "ACCESS_LOCATION",
        "READ_PHONE_STATE", "READ_EXTERNAL_STORAGE",
        "WRITE_EXTERNAL_STORAGE", "RECEIVE_BOOT_COMPLETE",
        "DISABLE_KEYGUARD", "WAKE_LOCK", "KILL_BACKGROUND_PROCESSES"
    )

    private val CRITICAL_API_PATTERNS = listOf(
        "Runtime;->exec", "ProcessBuilder",
        "DexClassLoader", "PathClassLoader",
        "SmsManager;->send", "DevicePolicyManager;->wipeData",
        "DevicePolicyManager;->lockNow", "AccessibilityService",
        "loadLibrary", "Cipher;->getInstance"
    )

    // Precomputed once — case-sensitive exact match, same as original dexStr.contains(pattern).
    private val CRITICAL_API_PATTERN_BYTES: List<ByteArray> by lazy {
        CRITICAL_API_PATTERNS.map { it.toByteArray(Charsets.ISO_8859_1) }
    }

    private val TRUSTED_PACKAGES = setOf(
        "com.google.android.apps.authenticator2",
        "com.google.android.gm", "com.google.android.youtube",
        "com.google.android.apps.maps", "com.google.android.apps.photos",
        "com.google.android.apps.docs", "com.google.android.calendar",
        "com.google.android.keep", "com.google.android.apps.translate",
        "com.google.android.apps.messaging", "com.google.android.dialer",
        "com.google.android.contacts", "com.google.android.deskclock",
        "com.google.android.calculator", "com.google.android.play.games",
        "com.google.android.gms", "com.google.android.webview",
        "com.android.chrome", "com.android.vending",
        "com.facebook.katana", "com.facebook.orca",
        "com.instagram.android", "com.whatsapp", "com.whatsapp.w4b",
        "com.microsoft.office.outlook", "com.microsoft.teams",
        "com.spotify.music", "com.twitter.android",
        "com.zhiliaoapp.musically", "com.linkedin.android",
        "com.dropbox.android", "com.netflix.mediaclient",
        "org.telegram.messenger", "com.snapchat.android",
        "com.discord", "com.slack", "com.pinterest",
        "com.reddit.frontpage", "com.adobe.reader",
        "com.zoom.videomeetings", "com.paypal.android.p2pmobile",
        "com.amazon.mShop.android.shopping"
    )

    private val TRUSTED_PREFIXES = setOf(
        "com.google.android.", "com.google.", "com.android.",
        "com.microsoft.", "com.facebook.", "com.instagram.",
        "com.whatsapp.", "com.samsung.android.", "com.sec.android.",
        "com.samsung.", "com.oneplus.", "com.oppo.", "com.vivo.",
        "com.xiaomi.", "com.miui.", "com.huawei.", "com.honor.",
        "com.realme.", "com.coloros.", "com.bbk.",
        "org.telegram.", "com.spotify.", "com.netflix.",
        "com.twitter.", "com.discord.", "com.slack.",
        "com.zoom.", "com.adobe.", "com.dropbox.",
        "org.mozilla.", "com.opera.", "com.duckduckgo.",
        "com.brave.", "com.tanjid.contrastguard"
    )

    data class RuleReport(
        val riskScore: Float,
        val criticalHits: Int,
        val highHits: Int,
        val mediumHits: Int,
        val criticalApis: Int,
        val flags: List<String>,
        val isKnownSafe: Boolean
    )

    /** Original entry point — reads the APK itself if no pre-read buffers are supplied. */
    fun analyze(context: Context, apkFile: File, packageName: String?): RuleReport {
        val isKnownSafe = packageName != null && (
                TRUSTED_PACKAGES.contains(packageName) ||
                        TRUSTED_PREFIXES.any { packageName.startsWith(it) }
                )
        if (isKnownSafe) {
            return RuleReport(3f, 0, 0, 0, 0, listOf("Verified publisher — trusted developer"), true)
        }
        val dexBuffers = try {
            DexReader.readAllDexBytes(apkFile)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        return analyze(context, apkFile, packageName, dexBuffers)
    }

    /** Preferred entry point for batch scanning: reuses DEX bytes already read by the caller. */
    fun analyze(context: Context, apkFile: File, packageName: String?, dexBuffers: List<ByteArray>): RuleReport {
        val flags = mutableListOf<String>()
        var criticalCount = 0
        var highCount = 0
        var mediumCount = 0
        var apiCount = 0

        val isKnownSafe = packageName != null && (
                TRUSTED_PACKAGES.contains(packageName) ||
                        TRUSTED_PREFIXES.any { packageName.startsWith(it) }
                )

        if (isKnownSafe) {
            flags.add("Verified publisher — trusted developer")
            return RuleReport(3f, 0, 0, 0, 0, flags, true)
        }

        try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath, PackageManager.GET_PERMISSIONS
            )
            val perms = packageInfo?.requestedPermissions?.toSet() ?: emptySet()
            val permsUpper = perms.map { it.uppercase() }.toSet()

            for (perm in permsUpper) {
                val short = perm.substringAfterLast('.')
                when {
                    CRITICAL_PERMS.any { perm.contains(it) } -> {
                        criticalCount++
                        flags.add("CRITICAL: $short")
                    }
                    HIGH_RISK_PERMS.any { perm.contains(it) } -> {
                        highCount++
                    }
                    MEDIUM_RISK_PERMS.any { perm.contains(it) } -> {
                        mediumCount++
                    }
                }
            }

            // Check each critical API pattern against each dex buffer; stop early once found.
            for ((patternIndex, patternBytes) in CRITICAL_API_PATTERN_BYTES.withIndex()) {
                var found = false
                for (bytes in dexBuffers) {
                    if (DexReader.containsExact(bytes, patternBytes)) {
                        found = true
                        break
                    }
                }
                if (found) {
                    apiCount++
                    flags.add("SUSPICIOUS API: ${CRITICAL_API_PATTERNS[patternIndex].take(30)}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val rawScore = (criticalCount * 18f) + (highCount * 5f) + (mediumCount * 1f) + (apiCount * 12f)
        return RuleReport(rawScore.coerceIn(0f, 100f), criticalCount, highCount, mediumCount, apiCount, flags, false)
    }
}