package com.lumecard.app.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun copyToClipboard(text: String, label: String) {
    val toolkit = Toolkit.getDefaultToolkit()
    val clipboard = toolkit.systemClipboard
    val selection = StringSelection(text)
    clipboard.setContents(selection, null)
}

actual fun getClipboardText(): String? {
    return try {
        val toolkit = Toolkit.getDefaultToolkit()
        val clipboard = toolkit.systemClipboard
        clipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (_: Exception) {
        null
    }
}
