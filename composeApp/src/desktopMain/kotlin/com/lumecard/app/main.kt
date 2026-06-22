package com.lumecard.app

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.lumecard.app.di.appModule
import org.jetbrains.skia.Image
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

fun main() = application {
    if (GlobalContext.getOrNull() == null) {
        startKoin { modules(appModule) }
    }

    val iconBytes = object {}.javaClass.getResourceAsStream("/icon_512.png")?.readBytes()
    val icon = iconBytes?.let {
        BitmapPainter(Image.makeFromEncoded(it).toComposeImageBitmap())
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "LumeCard",
        state = rememberWindowState(width = 1024.dp, height = 768.dp),
        icon = icon
    ) {
        App()
    }
}
