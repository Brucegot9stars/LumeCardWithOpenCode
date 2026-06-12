package com.lumecard.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LumeCard",
        state = rememberWindowState(width = 1024.dp, height = 768.dp)
    ) {
        App()
    }
}
