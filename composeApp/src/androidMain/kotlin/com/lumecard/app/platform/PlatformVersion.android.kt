package com.lumecard.app.platform

import android.content.Context
import com.lumecard.shared.database.AndroidContextHolder
import java.util.Properties

actual fun getAppVersion(): String {
    return try {
        val context = AndroidContextHolder.context
        if (context != null) {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: readVersionFromAsset(context) ?: "0.0.1"
        } else {
            readVersionFromAsset(null) ?: "0.0.1"
        }
    } catch (e: Exception) {
        readVersionFromAsset(AndroidContextHolder.context) ?: "0.0.1"
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
