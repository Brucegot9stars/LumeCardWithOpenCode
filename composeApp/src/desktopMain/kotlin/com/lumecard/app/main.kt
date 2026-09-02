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
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.JOptionPane

fun main() = application {
    if (GlobalContext.getOrNull() == null) {
        try {
            startKoin { modules(appModule) }
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val fullTrace = sw.toString()
            System.err.println("[LumeCard] FATAL: Koin initialization failed:\n$fullTrace")
            JOptionPane.showMessageDialog(
                null,
                "LumeCard failed to start:\n\n${e.message}\n\nFull error written to stderr.\nPlease check ~/.lumecard/ directory permissions.",
                "LumeCard - Startup Error",
                JOptionPane.ERROR_MESSAGE,
            )
            exitApplication()
        }
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
