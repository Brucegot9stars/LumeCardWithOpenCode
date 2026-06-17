package com.lumecard.app.platform

import com.lumecard.shared.AppVersion

actual fun getAppVersion(): String {
    return try {
        val context = org.koin.java.KoinJavaComponent.get<android.content.Context>(android.content.Context::class.java)
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: AppVersion.VERSION_NAME
    } catch (_: Exception) {
        try {
            val context = org.koin.java.KoinJavaComponent.get<android.app.Activity>(android.app.Activity::class.java)
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: AppVersion.VERSION_NAME
        } catch (_: Exception) {
            AppVersion.VERSION_NAME
        }
    }
}
