package com.lumecard.app.platform

import com.lumecard.shared.model.Rating

expect fun isDesktopPlatform(): Boolean

expect fun playRatingSound(rating: Rating)

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

/** Path to the local media directory for storing card media files. */
expect fun getMediaBasePath(): String

/** Read media (images, audio/video files) from the system clipboard, save with SHA-1 filename, return markdown references. */
expect fun pasteClipboardMedia(mediaDir: String): List<String>

/** Save a media file to [mediaDir] with SHA-1 dedup, return markdown reference or null. */
expect fun saveMediaFile(mediaDir: String, sourcePath: String): String?

data class ZipEntry(
    val path: String,
    val sourceFile: String
)

