package com.lumecard.app.platform

import java.io.File

actual fun getApkCacheDir(): File {
    val dir = File(System.getProperty("java.io.tmpdir") ?: System.getProperty("user.home") ?: ".", "lumecard_updates")
    dir.mkdirs()
    return dir
}
