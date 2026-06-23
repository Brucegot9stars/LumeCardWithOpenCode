package com.lumecard.app.platform

expect fun isDesktopPlatform(): Boolean

data class MediaFileEntry(
    val relativePath: String,
    val size: Long,
    val hash: String,
    val mtime: Long = 0
)

expect fun scanMediaDirectory(basePath: String): List<MediaFileEntry>

/** Cheap scan: path, size, mtime only — no hashing. */
data class RawFileEntry(val relativePath: String, val size: Long, val mtime: Long)
expect fun scanMediaDirectoryRaw(basePath: String): List<RawFileEntry>

/** Compute SHA-1 hash of a file (faster than SHA-256, sufficient for dedup). */
expect fun hashFileSha1(absPath: String): String

expect fun createZipPackage(outputPath: String, entries: List<ZipEntry>)

data class ZipEntry(
    val path: String,
    val sourceFile: String
)

