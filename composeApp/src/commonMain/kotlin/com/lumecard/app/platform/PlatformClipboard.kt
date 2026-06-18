package com.lumecard.app.platform

expect fun copyToClipboard(text: String, label: String = "LumeCard")

expect fun getClipboardText(): String?
