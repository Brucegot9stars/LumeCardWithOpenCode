package com.lumecard.app.platform

import com.lumecard.shared.AppVersion

actual fun getAppVersion(): String {
    return try {
        val props = java.util.Properties()
        val stream = object {}.javaClass.getResourceAsStream("/version.properties")
        if (stream != null) {
            props.load(stream)
            props.getProperty("APP_VERSION_NAME", AppVersion.VERSION_NAME)
        } else {
            AppVersion.VERSION_NAME
        }
    } catch (_: Exception) {
        AppVersion.VERSION_NAME
    }
}
