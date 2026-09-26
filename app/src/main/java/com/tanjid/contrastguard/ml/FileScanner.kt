package com.tanjid.contrastguard.ml

import android.os.Environment
import java.io.File

object FileScanner {

    enum class FileType(val label: String, val extensions: List<String>) {
        IMAGES("Images", listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic")),
        DOCUMENTS("Documents", listOf(".doc", ".docx", ".txt", ".rtf", ".odt", ".xls", ".xlsx", ".ppt", ".pptx")),
        PDFS("PDFs", listOf(".pdf")),
        MUSIC("Music", listOf(".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac", ".wma")),
        VIDEOS("Videos", listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".wmv")),
        APKS("APK Files", listOf(".apk")),
        ARCHIVES("Archives", listOf(".zip", ".rar", ".7z", ".tar", ".gz")),
        ALL("All Files", emptyList())
    }

    data class FileReport(
        val filePath: String,
        val fileName: String,
        val fileSize: Long,
        val fileType: String,
        val isSuspicious: Boolean,
        val reason: String
    )

    data class ScanSummary(
        val totalScanned: Int,
        val suspiciousFound: Int,
        val safeFiles: Int,
        val reports: List<FileReport>
    )

    // Suspicious file patterns
    private val SUSPICIOUS_EXTENSIONS = setOf(
        ".apk.jpg", ".exe", ".bat", ".cmd", ".vbs", ".scr",
        ".jar.jpg", ".apk.pdf", ".apk.mp3", ".exe.pdf"
    )

    private val SUSPICIOUS_NAME_PATTERNS = listOf(
        "hack", "cracked", "mod_", "patched",
        "keygen", "activator", "trojan",
        "backdoor", "spy", "keylog",
        "rootkit", "botnet", ".apk.jpg", ".jpg.apk"
    )

    fun scanDevice(
        type: FileType,
        maxFiles: Int = 5000,
        onProgress: (scannedCount: Int, currentPath: String) -> Unit
    ): ScanSummary {
        val reports = mutableListOf<FileReport>()
        val rootDirs = listOfNotNull(
            Environment.getExternalStorageDirectory(),
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/sdcard/DCIM"),
            File("/sdcard/Pictures"),
            File("/sdcard/Movies"),
            File("/sdcard/Music"),
            File("/sdcard/WhatsApp"),
            File("/sdcard/Telegram")
        ).distinct().filter { it.exists() && it.canRead() }

        var scanned = 0
        var suspicious = 0

        for (root in rootDirs) {
            if (scanned >= maxFiles) break
            scanDirectory(root, type, reports, scanned, maxFiles) { count, path, isSus ->
                scanned = count
                if (isSus) suspicious++
                onProgress(scanned, path)
            }
        }

        return ScanSummary(
            totalScanned = scanned,
            suspiciousFound = suspicious,
            safeFiles = scanned - suspicious,
            reports = reports.filter { it.isSuspicious }.take(200)
        )
    }

    private fun scanDirectory(
        dir: File,
        type: FileType,
        reports: MutableList<FileReport>,
        startCount: Int,
        maxFiles: Int,
        onFileScanned: (Int, String, Boolean) -> Unit
    ): Int {
        var count = startCount
        if (count >= maxFiles) return count
        if (!dir.exists() || !dir.canRead()) return count
        if (dir.name.startsWith(".")) return count  // Skip hidden

        try {
            val files = dir.listFiles() ?: return count

            for (file in files) {
                if (count >= maxFiles) break

                if (file.isDirectory) {
                    count = scanDirectory(file, type, reports, count, maxFiles, onFileScanned)
                    continue
                }

                val fileName = file.name.lowercase()
                val matchesType = when (type) {
                    FileType.ALL -> true
                    else -> type.extensions.any { fileName.endsWith(it) }
                }

                if (!matchesType) continue

                count++
                val (isSus, reason) = analyzeFile(file, fileName)

                reports.add(
                    FileReport(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        fileSize = file.length(),
                        fileType = fileName.substringAfterLast('.', "unknown"),
                        isSuspicious = isSus,
                        reason = reason
                    )
                )

                onFileScanned(count, file.absolutePath, isSus)
            }
        } catch (e: Exception) {
            // Skip inaccessible directories
        }

        return count
    }

    private fun analyzeFile(file: File, fileName: String): Pair<Boolean, String> {
        // Check double extensions
        for (susExt in SUSPICIOUS_EXTENSIONS) {
            if (fileName.contains(susExt)) {
                return true to "Double extension detected — file disguised as media"
            }
        }

        // Check suspicious keywords in filename
        for (pattern in SUSPICIOUS_NAME_PATTERNS) {
            if (fileName.contains(pattern)) {
                return true to "Suspicious filename pattern: '$pattern'"
            }
        }

        // Check APK file signature (magic bytes)
        if (!fileName.endsWith(".apk")) {
            try {
                val bytes = file.inputStream().use { it.readNBytes(4) }
                // ZIP magic bytes = PK\x03\x04 (APKs are ZIPs)
                if (bytes.size >= 4 && bytes[0] == 0x50.toByte() &&
                    bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() &&
                    bytes[3] == 0x04.toByte()) {

                    // If a "picture" or "document" has ZIP structure, it's suspicious
                    val ext = fileName.substringAfterLast('.', "")
                    if (ext in setOf("jpg", "jpeg", "png", "pdf", "mp3", "mp4", "doc", "txt")) {
                        return true to "File claims to be $ext but contains executable ZIP data"
                    }
                }
            } catch (e: Exception) {
                // Can't read, ignore
            }
        }

        // Empty file
        if (file.length() == 0L) {
            return true to "Empty file — possible placeholder for malware payload"
        }

        // Enormous "text" or "image" files
        val ext = fileName.substringAfterLast('.', "")
        if (ext in setOf("txt", "jpg", "png") && file.length() > 500 * 1024 * 1024) {
            return true to "Unusually large size for a $ext file (${file.length() / 1024 / 1024} MB)"
        }

        return false to "File appears clean"
    }
}