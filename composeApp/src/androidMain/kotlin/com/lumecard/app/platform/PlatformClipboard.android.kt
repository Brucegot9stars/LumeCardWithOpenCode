package com.lumecard.app.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.lumecard.shared.database.AndroidContextHolder

actual fun copyToClipboard(text: String, label: String) {
    try {
        val context = AndroidContextHolder.context
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    } catch (_: Exception) { }
}

actual fun getClipboardText(): String? {
    return try {
        val context = AndroidContextHolder.context
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    } catch (_: Exception) { null }
}
