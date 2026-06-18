package com.lumecard.app.platform

import android.content.Context
import com.lumecard.shared.AppVersion
import org.koin.java.KoinJavaComponent

actual fun getAppVersion(): String {
    return try {
        val context = KoinJavaComponent.get<Context>(Context::class.java)
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: AppVersion.VERSION_NAME
    } catch (e: Exception) {
        AppVersion.VERSION_NAME
    }
}
