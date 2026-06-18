package com.lumecard.app.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.lumecard.shared.database.AndroidContextHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private var pendingResult: String? = null

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return try {
        val context = AndroidContextHolder.context ?: return null
        val file = File(context.cacheDir, suggestedName)
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

actual suspend fun pickOpenFile(mimeType: String): String? {
    return suspendCancellableCoroutine { cont ->
        try {
            val activity = AndroidContextHolder.context as? Activity
            if (activity == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
            }
            activity.startActivityForResult(intent, 9999)
            cont.resume(null)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }
}

actual fun readFileContent(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        null
    }
}

actual fun writeFileContent(path: String, content: String): Boolean {
    return try {
        File(path).parentFile?.mkdirs()
        File(path).writeText(content)
        true
    } catch (e: Exception) {
        false
    }
}
