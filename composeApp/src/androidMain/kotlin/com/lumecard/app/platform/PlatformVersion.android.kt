package com.lumecard.app.platform

import android.content.Context
import android.content.pm.PackageManager

actual fun getAppVersion(): String {
    return try {
        val context = org.koin.java.KoinJavaComponent.get<Context>(Context::class.java)
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}
