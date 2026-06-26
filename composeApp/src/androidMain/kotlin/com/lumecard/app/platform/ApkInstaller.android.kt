package com.lumecard.app.platform

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

actual fun installApk(apkPath: String): Boolean {
    return try {
        val context = AndroidContextHolder.context
        val file = File(apkPath)
        if (!file.exists()) return false

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        true
    } catch (e: SecurityException) {
        false
    } catch (e: Exception) {
        false
    }
}
