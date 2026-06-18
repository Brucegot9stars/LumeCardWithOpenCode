package com.lumecard.app.platform

import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

actual fun getApkCacheDir(): File {
    val context = AndroidContextHolder.context
    val dir = File(context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "."), "apk_updates")
    dir.mkdirs()
    return dir
}
