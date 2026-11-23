package com.lumecard.app.platform

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.lumecard.shared.database.AndroidContextHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Matches the desktop reader; large enough for a 100k-card export, small enough to stay safe. */
private const val MAX_TEXT_BYTES = 64L * 1024 * 1024

object FilePickerState {
    var activity: android.app.Activity? = null
    var launcher: ActivityResultLauncher<Intent>? = null
    var waitingContinuation: kotlin.coroutines.Continuation<String?>? = null

    fun onResult(uri: String?) {
        waitingContinuation?.resume(uri)
        waitingContinuation = null
    }

    fun createLauncher(activity: ComponentActivity): ActivityResultLauncher<Intent> {
        return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data?.toString() else null
            onResult(uri)
        }
    }
}

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return suspendCancellableCoroutine { cont ->
        val prev = FilePickerState.waitingContinuation
        if (prev != null) prev.resume(null)
        cont.invokeOnCancellation { FilePickerState.waitingContinuation = null }
        FilePickerState.waitingContinuation = cont
        try {
            val launcher = FilePickerState.launcher
            if (launcher == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimeType == "*/*") "application/json" else mimeType
                putExtra(Intent.EXTRA_TITLE, suggestedName)
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }
}

actual suspend fun pickOpenFile(mimeType: String, initialDirectory: String?): String? {
    return suspendCancellableCoroutine { cont ->
        val prev = FilePickerState.waitingContinuation
        if (prev != null) prev.resume(null)
        cont.invokeOnCancellation { FilePickerState.waitingContinuation = null }
        FilePickerState.waitingContinuation = cont
        try {
            val launcher = FilePickerState.launcher
            if (launcher == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                // `*/*` means "any file" (the media picker needs images); a zip request is widened
                // to the document MIME types an export can actually carry. Previously `*/*` was
                // narrowed to `application/json`, which made the image picker show no images at
                // all, and `application/*` hid `.json` files that providers report as `text/plain`.
                type = when (mimeType) {
                    "application/zip" -> "*/*"
                    else -> mimeType
                }
                if (mimeType == "application/zip") {
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "application/json",
                            "application/zip",
                            "application/octet-stream",
                            "text/plain",
                            "text/json",
                        ),
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialDirectory != null) {
                    try {
                        val uri = Uri.parse(initialDirectory)
                        putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
                    } catch (_: Exception) { }
                }
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }
}

actual fun readFileContent(path: String): String? {
    return try {
        val context = AndroidContextHolder.context
        val uri = Uri.parse(path)
        if (uri.scheme == "content" || uri.scheme == "file") {
            // `readText()` grows without a bound: a malicious pick could hand us a 1 GB stream and
            // OOM before any size check runs. Read with a hard cap instead.
            context.contentResolver.openInputStream(uri)?.use { stream -> readBounded(stream) }
        } else {
            val file = java.io.File(path)
            if (!file.exists() || file.length() > MAX_TEXT_BYTES) null else file.readText(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Bounded decode. Counts **bytes** (not chars) so the limit means the same thing as the desktop
 * reader; a char-based check would let a CJK file twice as large through on Android only.
 */
private fun readBounded(stream: java.io.InputStream): String? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        if (total > MAX_TEXT_BYTES) return null
        out.write(buffer, 0, read)
    }
    return out.toString("UTF-8")
}

actual suspend fun pickMediaFile(): String? {
    return pickOpenFile("*/*")
}

actual fun writeFileContent(path: String, content: String): Boolean {
    return try {
        val context = AndroidContextHolder.context
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

actual fun fileParentDirectory(filePath: String): String? {
    return try {
        val uri = Uri.parse(filePath)
        if (uri.scheme == "file") {
            java.io.File(uri.path ?: return null).parentFile?.absolutePath
        } else if (uri.scheme == "content") {
            filePath.substringBeforeLast("/")
        } else {
            java.io.File(filePath).parentFile?.absolutePath
        }
    } catch (_: Exception) { null }
}
