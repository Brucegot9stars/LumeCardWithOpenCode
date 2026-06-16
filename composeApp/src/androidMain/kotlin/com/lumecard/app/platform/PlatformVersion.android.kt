package com.lumecard.app.platform

import android.content.Context

actual fun getAppVersion(): String {
    return try {
        val context = org.koin.java.KoinJavaComponent.get<Context>(Context::class.java)
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "1.2.0"
    } catch (_: Exception) {
        try {
            val context = org.koin.java.KoinJavaComponent.get<android.app.Activity>(android.app.Activity::class.java)
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.2.0"
        } catch (_: Exception) {
            "1.2.0"
        }
    }
}
