package com.lumecard.app.platform

import java.awt.Desktop
import java.io.File

actual fun installApk(apkPath: String): Boolean {
    return try {
        val file = File(apkPath)
        if (!file.exists()) return false
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file)
        } else {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", apkPath))
            } else {
                return false
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}
