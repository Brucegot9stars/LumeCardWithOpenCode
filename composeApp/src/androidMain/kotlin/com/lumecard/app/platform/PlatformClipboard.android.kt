package com.lumecard.app.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.lumecard.shared.database.AndroidContextHolder

actual fun copyToClipboard(text: String, label: String) {
    val context = AndroidContextHolder.context ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

actual fun getClipboardText(): String? {
    val context = AndroidContextHolder.context ?: return null
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}
