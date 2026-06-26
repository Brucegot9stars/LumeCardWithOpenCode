package com.lumecard.app.platform

import android.content.Context
import com.lumecard.shared.database.AndroidContextHolder
import java.util.Properties

actual fun getAppVersion(): String {
    return try {
        val ctx = AndroidContextHolder.context
        val pInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        pInfo.versionName ?: readVersionFromAsset(ctx) ?: "0.0.1"
    } catch (e: Exception) {
        readVersionFromAsset(try { AndroidContextHolder.context } catch (_: Exception) { null }) ?: "0.0.1"
    }
}

private fun readVersionFromAsset(context: Context?): String? {
    return try {
        val stream = context?.assets?.open("version.properties")
            ?: return null
        val props = Properties()
        stream.use { props.load(it) }
        props.getProperty("APP_VERSION_NAME")
    } catch (_: Exception) {
        null
    }
}
