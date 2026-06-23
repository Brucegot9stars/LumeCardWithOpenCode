package com.lumecard.app.platform

import android.content.Intent
import android.net.Uri
import com.lumecard.shared.database.AndroidContextHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FilePickerState {
    var activity: android.app.Activity? = null
    var waitingContinuation: kotlin.coroutines.Continuation<String?>? = null

    fun onResult(uri: String?) {
        waitingContinuation?.resume(uri)
        waitingContinuation = null
    }
}

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return suspendCancellableCoroutine { cont ->
        val prev = FilePickerState.waitingContinuation
        if (prev != null) prev.resume(null)
        cont.invokeOnCancellation { FilePickerState.waitingContinuation = null }
        FilePickerState.waitingContinuation = cont
        try {
            val activity = FilePickerState.activity
            if (activity == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimeType == "*/*") "application/json" else mimeType
                putExtra(Intent.EXTRA_TITLE, suggestedName)
            }
            activity.startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }
}

actual suspend fun pickOpenFile(mimeType: String): String? {
    return suspendCancellableCoroutine { cont ->
        val prev = FilePickerState.waitingContinuation
        if (prev != null) prev.resume(null)
        cont.invokeOnCancellation { FilePickerState.waitingContinuation = null }
        FilePickerState.waitingContinuation = cont
        try {
            val activity = FilePickerState.activity
            if (activity == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimeType == "*/*") "application/json" else mimeType
            }
            activity.startActivityForResult(intent, 1002)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }
}

actual fun readFileContent(path: String): String? {
    return try {
        val context = AndroidContextHolder.context ?: return null
        val uri = Uri.parse(path)
        if (uri.scheme == "content" || uri.scheme == "file") {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } else {
            val file = java.io.File(path)
            if (!file.exists()) null else file.readText(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}

actual suspend fun pickMediaFile(): String? {
    return pickOpenFile("*/*")
}

actual fun writeFileContent(path: String, content: String): Boolean {
    return try {
        val context = AndroidContextHolder.context ?: return false
        val uri = Uri.parse(path)
        if (uri.scheme == "content" || uri.scheme == "file") {
            val os = context.contentResolver.openOutputStream(uri, "wt")
                ?: return false
            os.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            true
        } else {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            file.exists() && file.length() > 0
        }
    } catch (e: Exception) {
        false
    }
}
