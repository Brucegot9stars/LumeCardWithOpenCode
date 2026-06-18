package com.lumecard.app.platform

import android.content.Context
import org.koin.java.KoinJavaComponent

actual fun getAppVersion(): String {
    return try {
        val context = KoinJavaComponent.get<Context>(Context::class.java)
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "0.3.0"
    } catch (e: Exception) {
        "0.3.0"
    }
}
