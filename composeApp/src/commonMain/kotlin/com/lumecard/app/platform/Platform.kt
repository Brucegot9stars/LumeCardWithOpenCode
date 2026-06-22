package com.lumecard.app.platform

expect fun isDesktopPlatform(): Boolean

data class MediaFileEntry(
    val relativePath: String,
    val size: Long,
    val hash: String
)

expect fun scanMediaDirectory(basePath: String): List<MediaFileEntry>

expect fun createZipPackage(outputPath: String, entries: List<ZipEntry>)

data class ZipEntry(
    val path: String,
    val sourceFile: String
)

