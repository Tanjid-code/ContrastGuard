package com.tanjid.contrastguard

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.tanjid.contrastguard.databinding.ActivityFileScanBinding
import com.tanjid.contrastguard.ml.FileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileScanBinding
    private var scanJob: Job? = null
    private var pendingScanType: FileScanner.FileType? = null
    private var folderUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if launched with a folder URI (from folder picker)
        val folderUriStr = intent.getStringExtra("folder_uri")
        if (folderUriStr != null) {
            folderUri = Uri.parse(folderUriStr)
            binding.layoutTypeSelection.visibility = View.GONE
            startFolderScan(folderUri!!)
            return
        }

        binding.btnBack.setOnClickListener {
            scanJob?.cancel()
            finish()
        }

        binding.btnScanImages.setOnClickListener { startScanWithPermission(FileScanner.FileType.IMAGES) }
        binding.btnScanDocs.setOnClickListener { startScanWithPermission(FileScanner.FileType.DOCUMENTS) }
        binding.btnScanPdfs.setOnClickListener { startScanWithPermission(FileScanner.FileType.PDFS) }
        binding.btnScanMusic.setOnClickListener { startScanWithPermission(FileScanner.FileType.MUSIC) }
        binding.btnScanVideos.setOnClickListener { startScanWithPermission(FileScanner.FileType.VIDEOS) }
        binding.btnScanApks.setOnClickListener { startScanWithPermission(FileScanner.FileType.APKS) }
        binding.btnScanAll.setOnClickListener { startScanWithPermission(FileScanner.FileType.ALL) }
    }

    private fun startScanWithPermission(type: FileScanner.FileType) {
        if (hasStoragePermission()) {
            startScan(type)
        } else {
            pendingScanType = type
            requestStoragePermission()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
                Toast.makeText(this, "Grant 'Allow access to all files' then come back", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Please enable storage access in Settings", Toast.LENGTH_LONG).show()
            }
        } else {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            ActivityCompat.requestPermissions(this, perms, 200)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingScanType?.let { startScan(it) }
        } else {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
        }
        pendingScanType = null
    }

    override fun onResume() {
        super.onResume()
        if (pendingScanType != null && hasStoragePermission()) {
            startScan(pendingScanType!!)
            pendingScanType = null
        }
    }

    private fun startScan(type: FileScanner.FileType) {
        scanJob?.cancel()

        binding.layoutTypeSelection.visibility = View.GONE
        binding.layoutProgress.visibility = View.VISIBLE
        binding.layoutResult.visibility = View.GONE

        binding.tvScanType.text = "Scanning ${type.label}..."
        binding.tvScanCount.text = "0 files"
        binding.tvCurrentFile.text = "Starting..."
        binding.progressScan.progress = 0

        scanJob = lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                FileScanner.scanDevice(type, maxFiles = 5000) { count, path ->
                    runOnUiThread {
                        binding.tvScanCount.text = "$count files scanned"
                        val shortPath = if (path.length > 55) "..." + path.takeLast(52) else path
                        binding.tvCurrentFile.text = shortPath
                        val percent = (count.coerceAtMost(5000) * 100 / 5000)
                        binding.progressScan.progress = percent
                    }
                }
            }

            showResults(summary)
        }
    }

    private fun startFolderScan(uri: Uri) {
        binding.layoutTypeSelection.visibility = View.GONE
        binding.layoutProgress.visibility = View.VISIBLE
        binding.layoutResult.visibility = View.GONE

        binding.tvScanType.text = "Scanning Selected Folder..."
        binding.tvScanCount.text = "0 files"
        binding.tvCurrentFile.text = "Starting..."
        binding.progressScan.progress = 0

        scanJob = lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                scanFolderUri(uri)
            }
            showResults(summary)
        }
    }

    private fun scanFolderUri(uri: Uri): FileScanner.ScanSummary {
        val reports = mutableListOf<FileScanner.FileReport>()
        var scanned = 0
        var suspicious = 0

        try {
            val docFile = DocumentFile.fromTreeUri(this, uri) ?: return FileScanner.ScanSummary(0, 0, 0, emptyList())

            scanDocFileRecursively(docFile, reports) { count, path, isSus ->
                scanned = count
                if (isSus) suspicious++
                runOnUiThread {
                    binding.tvScanCount.text = "$count files scanned"
                    val shortPath = if (path.length > 55) "..." + path.takeLast(52) else path
                    binding.tvCurrentFile.text = shortPath
                    binding.progressScan.progress = (count.coerceAtMost(2000) * 100 / 2000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return FileScanner.ScanSummary(
            totalScanned = scanned,
            suspiciousFound = suspicious,
            safeFiles = scanned - suspicious,
            reports = reports.filter { it.isSuspicious }.take(200)
        )
    }

    private var folderScanCount = 0
    private fun scanDocFileRecursively(
        docFile: DocumentFile,
        reports: MutableList<FileScanner.FileReport>,
        onProgress: (Int, String, Boolean) -> Unit
    ) {
        if (docFile.isFile) {
            folderScanCount++
            val name = docFile.name ?: "unknown"
            val (isSus, reason) = analyzeFileName(name, docFile.length())

            reports.add(
                FileScanner.FileReport(
                    filePath = docFile.uri.toString(),
                    fileName = name,
                    fileSize = docFile.length(),
                    fileType = name.substringAfterLast('.', "unknown"),
                    isSuspicious = isSus,
                    reason = reason
                )
            )
            onProgress(folderScanCount, name, isSus)
        } else if (docFile.isDirectory) {
            docFile.listFiles().forEach { scanDocFileRecursively(it, reports, onProgress) }
        }
    }

    private fun analyzeFileName(name: String, size: Long): Pair<Boolean, String> {
        val lower = name.lowercase()

        val doubleExts = listOf(".apk.jpg", ".apk.png", ".apk.pdf", ".apk.mp3",
            ".exe.pdf", ".jar.jpg", ".jpg.apk", ".pdf.exe")
        for (ext in doubleExts) {
            if (lower.contains(ext)) return true to "Double extension: disguised as media"
        }

        val susNames = listOf("hack", "cracked", "keygen", "trojan", "backdoor",
            "spy", "keylog", "rootkit", "botnet", "patched")
        for (n in susNames) {
            if (lower.contains(n)) return true to "Suspicious keyword in filename: '$n'"
        }

        if (size == 0L) return true to "Empty file — possible placeholder"

        return false to "Clean"
    }

    private fun showResults(summary: FileScanner.ScanSummary) {
        binding.layoutProgress.visibility = View.GONE
        binding.layoutResult.visibility = View.VISIBLE

        binding.tvTotalScanned.text = summary.totalScanned.toString()
        binding.tvSuspiciousCount.text = summary.suspiciousFound.toString()
        binding.tvSafeCount.text = summary.safeFiles.toString()

        if (summary.suspiciousFound == 0) {
            binding.tvResultTitle.text = "All Clean"
            binding.tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.safe))
            binding.tvResultDetail.text = "No suspicious files found among ${summary.totalScanned} scanned."
            binding.tvSuspiciousList.visibility = View.GONE
        } else {
            binding.tvResultTitle.text = "${summary.suspiciousFound} Suspicious Files Found"
            binding.tvResultTitle.setTextColor(ContextCompat.getColor(this, R.color.danger))
            binding.tvResultDetail.text = "Review these files carefully."
            binding.tvSuspiciousList.visibility = View.VISIBLE

            val sb = StringBuilder()
            summary.reports.take(50).forEach { report ->
                sb.appendLine("File: ${report.fileName}")
                sb.appendLine("Reason: ${report.reason}")
                sb.appendLine("Path: ${report.filePath}")
                sb.appendLine("---")
            }
            if (summary.suspiciousFound > 50) {
                sb.appendLine("... and ${summary.suspiciousFound - 50} more")
            }
            binding.tvSuspiciousList.text = sb.toString().trim()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
    }
}