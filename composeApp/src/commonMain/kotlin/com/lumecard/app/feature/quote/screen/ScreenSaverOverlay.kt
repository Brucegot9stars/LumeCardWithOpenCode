package com.lumecard.app.feature.quote.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import com.lumecard.app.feature.quote.viewer.QuoteViewer
import com.lumecard.shared.data.SplashQuoteData
import com.lumecard.shared.feature.quote.config.QuoteDisplayConfig
import com.lumecard.shared.feature.quote.config.QuoteDisplayMode

@Composable
fun ScreenSaverOverlay(
    currentQuote: SplashQuoteData?,
    config: QuoteDisplayConfig,
    globalBackgroundPath: String,
    onUserActivity: () -> Unit,
    onNextQuote: () -> Unit,
) {
    if (currentQuote == null) return

    DisposableEffect(Unit) {
        onUserActivity()
        onDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (config.allowTapToSkip) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUserActivity,
                    )
                } else Modifier
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        onUserActivity()
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    onUserActivity()
                    true
                } else false
            },
    ) {
        QuoteViewer(
            quote = currentQuote,
            config = config,
            globalBackgroundPath = globalBackgroundPath,
            onDismiss = onUserActivity,
            onNextQuote = onNextQuote,
        )
    }
}
