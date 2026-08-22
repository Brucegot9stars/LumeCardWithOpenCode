package com.lumecard.shared.data

import java.io.File

actual fun platformMkdirsForPath(path: String) {
    File(path).parentFile?.mkdirs()
}

actual fun platformWriteAllBytes(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

actual fun platformFileExists(path: String): Boolean = File(path).exists()

actual fun platformDeleteFile(path: String): Boolean = File(path).delete()
