package com.lumecard.app.platform

import java.io.File

actual fun platformGetFileName(path: String): String = File(path).name

actual fun platformJoinPath(base: String, vararg parts: String): String =
    File(base, parts.joinToString(File.separator)).absolutePath

actual fun platformNormalizePath(path: String): String = File(path).absolutePath

actual fun platformPathExists(path: String): Boolean = File(path).exists()

actual fun platformIsFile(path: String): Boolean = File(path).isFile

actual fun platformListFiles(dir: String): List<String> =
    File(dir).listFiles()?.map { it.absolutePath }.orEmpty()

actual fun platformGetFileNameWithoutExtension(path: String): String =
    File(path).nameWithoutExtension

actual fun platformGetUserHome(): String? =
    System.getenv("USERPROFILE") ?: System.getenv("HOME") ?: System.getProperty("user.home")

actual fun platformGetSystemProperty(key: String): String? = System.getProperty(key)

actual fun platformReadFileBytes(path: String): ByteArray? = try {
    File(path).readBytes()
} catch (_: Exception) { null }

actual fun platformWriteFileBytes(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

actual fun platformDeleteFile(path: String): Boolean = File(path).delete()

actual fun platformMkdirs(path: String): Boolean = File(path).mkdirs()

actual fun platformFileExists(path: String): Boolean = File(path).exists()

actual fun platformListFileNames(dir: String): List<String> =
    File(dir).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

actual fun platformReadFileText(path: String): String? = try {
    File(path).readText()
} catch (_: Exception) { null }

actual fun platformWriteFileText(path: String, text: String) {
    File(path).writeText(text)
}

actual fun platformGetParentDir(path: String): String = File(path).parent ?: ""
