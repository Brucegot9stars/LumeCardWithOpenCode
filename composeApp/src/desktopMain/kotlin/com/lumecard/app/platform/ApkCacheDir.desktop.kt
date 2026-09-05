package com.lumecard.app.platform

import java.io.File

private fun userHome(): String = System.getenv("USERPROFILE") ?: System.getenv("HOME") ?: System.getProperty("user.home")

actual fun getApkCacheDir(): File {
    val dir = File(System.getProperty("java.io.tmpdir") ?: userHome() ?: ".", "lumecard_updates")
    dir.mkdirs()
    return dir
}
