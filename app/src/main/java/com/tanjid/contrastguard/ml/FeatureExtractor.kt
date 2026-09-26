package com.tanjid.contrastguard.ml

import android.content.Context
import android.content.pm.PackageManager
import com.tanjid.contrastguard.model.RiskIndicator
import java.io.File

object FeatureExtractor {

    const val TOTAL_FEATURES = 299

    private val API_KEYWORDS = arrayOf(
        "TOTCLASSES", "XML", "WWW", "WINDOW", "UTIL", "UTF", "URL", "URI",
        "UDP", "TRANSPORT", "TOR", "TELEPHONY", "SYSTEM", "SUBSCRIBER",
        "SSL", "SOCKET", "SMS", "SIM", "SIGNATURE", "SHELL",
        "SHAREDPREFERENCES", "SERVICE", "SERVER", "SERIAL", "SENDTEXTMESSAGE",
        "SEND", "SD", "SCREENSHOT", "RUNTIME", "RUNNABLE",
        "RESOURCE", "REFLECT", "READ", "RAR", "PROXY",
        "PROPERT", "PROCESS", "PRIVATEKEY", "POWERMANAGER", "POLICY",
        "PNG", "PKG_NAME", "PERMISSION", "PDF", "PATH",
        "PARENT", "PACKET", "PACKAGE", "OUTPUTSTREAM", "ONRECEIVE",
        "OBJECT", "NOTIFICATION", "NEWINSTANCE", "NETWORK", "NETMASK",
        "NET", "NATIVE", "NAMING", "MKDIR", "MESSAGE",
        "MEMORY", "MEDIA", "MAP", "LOOKUP", "LONGITUDE",
        "LOCATION", "LOCALHOST", "LOADEDAPK", "LOADCLASS", "LOAD",
        "LATITUDE", "JSON", "JAR", "INVO", "INTENT",
        "INSTANCE", "INPUTSTREAM", "INPUTSOURCE", "IMEI", "HTTP",
        "HOST", "GOTO", "FTP", "FILE", "EXEC",
        "ENCRYPT", "DRIVER", "DEX", "DEVICE", "DECRYPT",
        "DECODE", "DATAGRAM", "CONTENT", "CONNECT", "CLONE",
        "CLIENT", "CIPHER", "CHECK", "CACHE", "BROADCAST",
        "BASE", "AUTH", "APP", "APK", "AES",
        "ADDRESS", "ACTIVITY", "ACCESS"
    )

    private val PERMISSION_FEATURES = arrayOf(
        "write", "broadcast",
        "WRITE_USE_APP_FEATURE_SURVEY", "WRITE_USER_DICTIONARY", "WRITE_SMS",
        "WRITE_SETTINGS", "WRITE_SECURE_SETTINGS", "WRITE_SECURE",
        "WRITE_PROFILE", "WRITE_OWNER_DATA", "WRITE_MEDIA_STORAGE",
        "WRITE_INTERNAL_STORAGE", "WRITE_HISTORY_BOOKMARKS", "WRITE_GSERVICES",
        "WRITE_EXTERNAL_STORAGE", "WRITE_CONTACTS", "WRITE_CALL_LOG",
        "WRITE_CALENDAR", "WRITE_APN_STORAGE", "WRITE_APN_SETTINGS",
        "WRITE_APN_SETTING", "WAKE_LOCK", "VIBRATE",
        "USES_POLICY_FORCE_LOCK", "UPDATE_SHORTCUT", "UPDATE_DEVICE_STATS",
        "UPDATE_BADGE", "UPDATE_APP_OPS_STATS", "UNINSTALL_SHORTCUT",
        "UAPUSH_MESSAGE", "SYSTEM_OVERLAY_WINDOW", "SYSTEM_ALERT_WINDOW",
        "SUBSCRIBED_FEEDS_WRITE", "SUBSCRIBED_FEEDS_READ", "SIM_STATE_READY",
        "SIGNAL_PERSISTENT_PROCESSES", "SHUTDOWN", "SHARE",
        "SET_WALLPAPER_HINTS", "SET_WALLPAPER_COMPONENT", "SET_WALLPAPER",
        "SET_TIME_ZONE", "SET_TIME", "SET_PROCESS_FOREGROUND",
        "SET_PREFERRED_APPLICATIONS", "SET_POINTER_SPEED", "SET_ALWAYS_FINISH",
        "SET_ALARM", "SET_ACTIVITY_WATCHER", "SEND_SMS",
        "SEND_RESPOND_VIA_MESSAGE", "SEND_DOWNLOAD_COMPLETED_INTENTS",
        "RUN_INSTRUMENTATION", "RIDE_EXTERNAL_STORAGE", "RESTART_PACKAGES",
        "REQUEST_SUPERUSER", "REQUEST_INSTALL_PACKAGES",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "REQUEST",
        "REORDER_TASKS", "RECORD_VIDEO", "RECORD_AUDIO",
        "RECEIVE_WAP_PUSH", "RECEIVE_USER_PRESENT", "RECEIVE_SMS",
        "RECEIVE_SENDTO", "RECEIVE_MMS", "RECEIVE_BOOT_COMPLETE",
        "REBOOT", "REAL_GET_TASKS", "READ_USER_DICTIONARY",
        "READ_SMS", "READ_SETTINGS", "READ_SECURE_SETTINGS",
        "READ_PROFILE", "READ_PHONE_STATE", "READ_OWNER_DATA",
        "READ_MMS", "READ_LOGS", "READ_INTERNAL_STORAGE",
        "READ_INPUT_STATE", "READ_HISTORY_BOOKMARKS", "READ_EXTERNAL_STORAGE",
        "READ_CONTENT_PROVIDER", "READ_CONTACTS", "READ_CALL_LOG",
        "READ_CALENDAR", "READ_APP_BADGE", "RAISED_THREAD_PRIORITY",
        "QUICKBOOT_POWERON", "PROVIDER_INSERT_BADGE", "PROCESS_OUTGOING_CALLS",
        "PROCESS_INCOMING_CALLS", "PLUGIN", "PERSISTENT_ACTIVITY",
        "PERMISSION_NAME", "PAYMENT", "PACKAGE_USAGE_STATS",
        "MOVE_PACKAGE", "MOUNT_UNMOUNT_FILESYSTEMS", "MOUNT_FORMAT_FILESYSTEMS",
        "MODIFY_PHONE_STATE", "MODIFY_AUDIO_SETTINGS", "MIPUSH_RECEIVE",
        "MEDIA_CONTENT_CONTROL", "MASTER_CLEAR", "MAPS_RECEIVE",
        "MANAGE_APP_TOKENS", "MANAGE_ACCOUNTS", "LAUNCH",
        "KILL_BACKGROUND_PROCESSES", "JPUSH_MESSAGE", "JOLOPAY",
        "INTERNET", "INTERNAL_SYSTEM_WINDOW", "INTERACT_ACROSS_USERS_FULL",
        "INTERACT_ACROSS_USERS", "INSTALL_SHORTCUT", "INSTALL_PACKAGES",
        "HARDWARE_TEST", "GLOBAL_SEARCH_CONTROL", "GLOBAL_SEARCH",
        "GET_TASKS", "GET_PACKAGE_SIZE", "GET_ACCOUNTS",
        "FULL_SCREEN", "FORCE_STOP_PACKAGES", "FORCE_BACK",
        "FLASHLIGHT", "FACTORY_TEST", "EXPAND_STATUS_BAR",
        "DOWNLOAD_WITHOUT_NOTIFICATION", "DISABLE_KEYGUARD", "DIAGNOSTIC",
        "DEVICE_POWER", "DELETE_PACKAGES", "DELETE_CACHE_FILES",
        "CREATE_SHORTCUT", "CLEAR_APP_USER_DATA", "CLEAR_APP_CACHE",
        "CHANGE_WIMAX_STATE", "CHANGE_WIFI_STATE", "CHANGE_WIFI_MULTICAST_STATE",
        "CHANGE_NETWORK_STATE", "CHANGE_CONFIGURATION", "CHANGE_BADGE",
        "CAPTURE_AUDIO_OUTPUT", "CAMERA", "CALL_PRIVILEGED",
        "CALL_PHONE", "BROADCAST_WAP_PUSH", "BROADCAST_STICKY",
        "BROADCAST_SMS", "BROADCAST_PACKAGE_REPLACED", "BROADCAST_PACKAGE_REMOVED",
        "BROADCAST_PACKAGE_INSTALL", "BROADCAST_PACKAGE_ADDED", "BROADCAST_BADGE",
        "BLUETOOTH_ADMIN", "BLUETOOTH", "BIND_WALLPAPER",
        "BIND_VPN_SERVICE", "BIND_SERVICE_ADMIN", "BIND_REMOTEVIEWS",
        "BIND_QUICK_SETTINGS_TILE", "BIND_JOB_SERVICE", "BIND_INPUT_METHOD",
        "BIND_DEVICE_ADMIN", "BIND_APPWIDGET", "BIND_ACCESSIBILITY_SERVICE",
        "BATTERY_STATS", "ANSWER_PHONE", "ADD_SYSTEM_SERVICE",
        "ACCESS_WIMAX_STATE", "ACCESS_WIFI_STATE", "ACCESS_WAKE_LOCK",
        "ACCESS_SURFACE_FLINGER", "ACCESS_SUPERUSER", "ACCESS_NETWORK_STATE",
        "ACCESS_MTK_MMHW", "ACCESS_MOCK_LOCATION", "ACCESS_LOCATION",
        "ACCESS_LAUNCHER_DATA", "ACCESS_FIND_LOCATION", "ACCESS_DOWNLOAD_MANAGER",
        "ACCESS_COARSE", "ACCESS_CHECKIN_PROPERTIES", "ACCESS_CACHE_FILESYSTEM",
        "ACCESS_BROWSER", "ACCESS_BLUETOOTH_SHARE", "ACCESS_ASSISTED_GPS"
    )

    // Precomputed once, ever — not per app, not per call.
    private val KEYWORD_BYTES_LOWER: Array<ByteArray> by lazy {
        Array(API_KEYWORDS.size) { i -> API_KEYWORDS[i].lowercase().toByteArray(Charsets.ISO_8859_1) }
    }
    private val LCOM_BYTES = "Lcom/".toByteArray(Charsets.ISO_8859_1)
    private val LANDROID_BYTES = "Landroid/".toByteArray(Charsets.ISO_8859_1)
    private val LJAVA_BYTES = "Ljava/".toByteArray(Charsets.ISO_8859_1)

    /**
     * Original entry point — kept for any existing caller (ScanActivity,
     * InstalledAppsActivity, FileScanActivity, etc.) that doesn't have
     * pre-read DEX bytes. Reads the APK itself, once, via DexReader.
     */
    fun extractFeatures(context: Context, apkFile: File): FloatArray {
        val dexBuffers = try {
            DexReader.readAllDexBytes(apkFile)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        return extractFeatures(context, apkFile, dexBuffers)
    }

    /**
     * Preferred entry point for batch scanning: pass in DEX bytes that were
     * already read once (shared with RuleEngine) instead of re-reading the zip.
     */
    fun extractFeatures(context: Context, apkFile: File, dexBuffers: List<ByteArray>): FloatArray {
        val features = FloatArray(TOTAL_FEATURES) { 0.0f }
        try {
            var classCount = 0
            val counts = IntArray(API_KEYWORDS.size)

            for (bytes in dexBuffers) {
                classCount += DexReader.countExact(bytes, LCOM_BYTES)
                classCount += DexReader.countExact(bytes, LANDROID_BYTES)
                classCount += DexReader.countExact(bytes, LJAVA_BYTES)

                for (i in 1 until API_KEYWORDS.size) {
                    counts[i] += DexReader.countCaseInsensitive(bytes, KEYWORD_BYTES_LOWER[i])
                }
                // 'bytes' falls out of scope at the end of this iteration —
                // peak memory is bounded to one dex file, not the whole app.
            }

            features[0] = classCount.toFloat().coerceAtLeast(1f)
            for (i in 1 until API_KEYWORDS.size) {
                features[i] = counts[i].toFloat()
            }

            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_PERMISSIONS
            )
            val declaredPerms = packageInfo?.requestedPermissions?.toSet() ?: emptySet()
            val declaredPermsUpper = declaredPerms.map { it.uppercase() }.toSet()

            val permOffset = API_KEYWORDS.size

            for (i in PERMISSION_FEATURES.indices) {
                val idx = permOffset + i
                if (idx >= TOTAL_FEATURES) break

                val featName = PERMISSION_FEATURES[i].uppercase()
                val hasPermission = declaredPermsUpper.any { perm ->
                    perm.contains(featName) || perm.endsWith(".$featName")
                }
                features[idx] = if (hasPermission) 1.0f else 0.0f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return features
    }

    fun getRiskIndicators(features: FloatArray): List<RiskIndicator> {
        val risks = mutableListOf<RiskIndicator>()
        val permOffset = API_KEYWORDS.size

        val dangerousPerms = mapOf(
            "SEND_SMS" to Triple("SMS Sending Access", "Can send premium-rate SMS without asking", "critical"),
            "READ_SMS" to Triple("SMS Reading Access", "Can read private SMS messages and OTP verification codes", "critical"),
            "RECEIVE_SMS" to Triple("SMS Interception", "Can capture incoming notifications and security codes", "critical"),
            "BIND_DEVICE_ADMIN" to Triple("Device Administration", "Can perform high-level system functions like locking the screen", "critical"),
            "BIND_ACCESSIBILITY_SERVICE" to Triple("Accessibility Access", "Can view all onscreen elements and capture input", "critical"),
            "INSTALL_PACKAGES" to Triple("Install Packages", "Can install applications silently", "critical"),
            "SYSTEM_ALERT_WINDOW" to Triple("Screen Overlay System", "Can place phishing overlays on top of trusted banking apps", "high"),
            "RECORD_AUDIO" to Triple("Microphone Recording", "Can record environmental audio and voice calls", "high"),
            "CAMERA" to Triple("Camera Operations", "Can capture photos and record video streams", "high"),
            "ACCESS_FINE_LOCATION" to Triple("GPS Tracking", "Can determine your exact real-time physical coordinates", "high"),
            "READ_CONTACTS" to Triple("Contacts Access", "Can copy and export your address book", "medium"),
            "READ_CALL_LOG" to Triple("Call Log Analyzer", "Can read history of calls", "medium"),
            "RECEIVE_BOOT_COMPLETE" to Triple("Auto-Start Service", "Configured to run automatically when the device starts up", "medium")
        )

        for (i in PERMISSION_FEATURES.indices) {
            val idx = permOffset + i
            if (idx < features.size && features[idx] > 0.5f) {
                val name = PERMISSION_FEATURES[i]
                dangerousPerms[name]?.let { (title, desc, severity) ->
                    risks.add(RiskIndicator(name, title, desc, severity))
                }
            }
        }
        return risks
    }

    fun generateExplanation(
        isMalware: Boolean,
        modelScore: Float,
        ruleScore: Float,
        hybridScore: Float,
        risks: List<RiskIndicator>,
        features: FloatArray
    ): String {
        val sb = StringBuilder()
        if (isMalware) {
            sb.appendLine("🚨 HIGH-RISK THREAT DETECTED")
            sb.appendLine("This application behaves similarly to known malware signatures in our database.")
            sb.appendLine()
            if (risks.isNotEmpty()) {
                sb.appendLine("Primary Risk Indicators:")
                risks.take(3).forEach { r ->
                    sb.appendLine("  • ${r.title} (${r.severity.uppercase()}): ${r.description}")
                }
            }
            if (features[API_KEYWORDS.indexOf("EXEC")] > 10f) {
                sb.appendLine("  • Command Execution: System execution keywords detected in bytecode.")
            }
        } else {
            sb.appendLine("🛡️ APPLICATION IS SAFE")
            sb.appendLine("Analysis complete. This application contains normal software execution patterns.")
            sb.appendLine()
            if (risks.isNotEmpty()) {
                sb.appendLine("Standard Permissions Requested:")
                risks.take(2).forEach { r ->
                    sb.appendLine("  • ${r.title}: Legitimate use case expected.")
                }
            }
        }
        return sb.toString()
    }
}