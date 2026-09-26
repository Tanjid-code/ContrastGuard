package com.tanjid.contrastguard.ml

import java.io.File
import java.util.zip.ZipFile

/**
 * Reads all .dex entries from an APK exactly once, as raw byte arrays.
 * FeatureExtractor and RuleEngine both consume this instead of each
 * independently opening the zip and building their own String copy of
 * the DEX content. This is the fix for the duplicate-allocation /
 * OOM-risk pattern during batch scans.
 */
object DexReader {

    fun readAllDexBytes(apkFile: File): List<ByteArray> {
        val buffers = mutableListOf<ByteArray>()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".dex")) {
                    zip.getInputStream(entry).use { input ->
                        buffers.add(input.readBytes())
                    }
                }
            }
        }
        return buffers
    }

    /** Case-sensitive, non-overlapping count of `needle` in `haystack`. */
    fun countExact(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var i = 0
        val limit = haystack.size - needle.size
        outer@ while (i <= limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            count++
            i += needle.size
        }
        return count
    }

    /** Case-insensitive, non-overlapping count. `needleLower` must already be lowercase ASCII bytes. */
    fun countCaseInsensitive(haystack: ByteArray, needleLower: ByteArray): Int {
        if (needleLower.isEmpty()) return 0
        var count = 0
        var i = 0
        val limit = haystack.size - needleLower.size
        outer@ while (i <= limit) {
            for (j in needleLower.indices) {
                if (toLowerByte(haystack[i + j]) != needleLower[j]) {
                    i++
                    continue@outer
                }
            }
            count++
            i += needleLower.size
        }
        return count
    }

    /** Case-sensitive containment check (stops at first match, for boolean flags). */
    fun containsExact(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return false
        var i = 0
        val limit = haystack.size - needle.size
        outer@ while (i <= limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return true
        }
        return false
    }

    private fun toLowerByte(b: Byte): Byte {
        val i = b.toInt() and 0xFF
        return if (i in 65..90) (i + 32).toByte() else b
    }
}