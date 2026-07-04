package com.lumecard.app.feature.quote.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lumecard.app.font.FontRegistry
import com.lumecard.shared.data.*
import com.lumecard.shared.feature.quote.config.QuoteDisplayConfig
import kotlinx.coroutines.delay

@Composable
fun QuoteViewer(
    quote: SplashQuoteData,
    config: QuoteDisplayConfig,
    overrideConfig: QuoteOverrideConfig? = null,
    globalBackgroundPath: String = "",
    onDismiss: () -> Unit = {},
    onNextQuote: (() -> Unit)? = null,
) {
    val effectiveOverride = overrideConfig ?: quote.overrideConfig
    val effectiveDirection = effectiveOverride?.direction?.resolveOverridden(config.defaultDirection) ?: config.defaultDirection

    val effectiveFontFamily = remember(effectiveOverride?.font, config.defaultFont) {
        val fontId = effectiveOverride?.font?.takeIf { it.isNotBlank() } ?: config.defaultFont
        if (fontId.isNotBlank()) FontRegistry.resolveFontFamily(fontId) else FontFamily.Default
    }

    val effectiveFontSize = effectiveOverride?.fontSize?.resolveOverridden(config.defaultFontSize)?.let {
        if (it > 0f) it.sp else 24.sp
    } ?: if (config.defaultFontSize > 0f) config.defaultFontSize.sp else 24.sp
    val effectiveShowAuthor = effectiveOverride?.showAuthor?.resolveOverridden(config.showAuthor) ?: config.showAuthor
    val effectiveBackground = resolveBackground(effectiveOverride?.background, globalBackgroundPath)

    var autoDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(config.autoDismiss, config.dismissDurationMs) {
        if (config.autoDismiss) {
            delay(config.dismissDurationMs)
            if (!autoDismissed) {
                autoDismissed = true
                onDismiss()
            }
        }
    }

    val tapModifier = if (config.allowTapToSkip) {
        Modifier.clickable {
            if (!autoDismissed) {
                autoDismissed = true
                if (config.autoRotate && onNextQuote != null) {
                    onNextQuote()
                } else {
                    onDismiss()
                }
            }
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(tapModifier),
    ) {
        if (config.enableBackground) {
            QuoteBackgroundBox(
                background = effectiveBackground,
                modifier = Modifier.fillMaxSize(),
            ) {
                QuoteContent(
                    quote = quote,
                    config = config,
                    direction = effectiveDirection,
                    fontFamily = effectiveFontFamily,
                    fontSize = effectiveFontSize,
                    showAuthor = effectiveShowAuthor,
                    layoutOverride = effectiveOverride?.layout,
                )
            }
        } else {
            QuoteBackgroundBox(
                background = ResolvedBackground.Default,
                modifier = Modifier.fillMaxSize(),
            ) {
                QuoteContent(
                    quote = quote,
                    config = config,
                    direction = effectiveDirection,
                    fontFamily = effectiveFontFamily,
                    fontSize = effectiveFontSize,
                    showAuthor = effectiveShowAuthor,
                    layoutOverride = effectiveOverride?.layout,
                )
            }
        }

        if (config.showPreviewBadge) {
            Text(
                text = "PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun QuoteContent(
    quote: SplashQuoteData,
    config: QuoteDisplayConfig,
    direction: SplashQuoteDirection,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    showAuthor: Boolean,
    layoutOverride: QuoteLayoutOverride?,
) {
    when (direction) {
        SplashQuoteDirection.HORIZONTAL -> {
            AnimatedQuoteContent(
                text = quote.text,
                author = quote.author,
                animationStyle = config.animationStyle,
                enabled = config.enableAnimation,
            ) { visibleText, visibleAuthor ->
                QuoteTextLayout(
                    text = visibleText,
                    author = visibleAuthor,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    showAuthor = showAuthor,
                    layoutOverride = layoutOverride,
                )
            }
        }
        SplashQuoteDirection.VERTICAL -> {
            VerticalQuoteContent(
                quote = quote,
                config = config,
                fontFamily = fontFamily,
                fontSize = fontSize,
                showAuthor = showAuthor,
                layoutOverride = layoutOverride,
            )
        }
    }
}

@Composable
private fun VerticalQuoteContent(
    quote: SplashQuoteData,
    config: QuoteDisplayConfig,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    showAuthor: Boolean,
    layoutOverride: QuoteLayoutOverride?,
) {
    val paddingHorizontal = layoutOverride?.pagePadding?.dp ?: 32.dp
    val verticalPos = layoutOverride?.verticalPosition ?: VerticalPosition.CENTER
    val verticalArrangement = when (verticalPos) {
        VerticalPosition.TOP -> Arrangement.Top
        VerticalPosition.CENTER -> Arrangement.Center
        VerticalPosition.BOTTOM -> Arrangement.Bottom
    }
    val textAlign = com.lumecard.app.feature.quote.viewer.resolveTextAlign(layoutOverride?.textAlign, androidx.compose.ui.text.style.TextAlign.Center as androidx.compose.ui.text.style.TextAlign)

    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = paddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedQuoteContent(
                text = quote.text,
                author = quote.author,
                animationStyle = config.animationStyle,
                enabled = config.enableAnimation,
            ) { visibleText, visibleAuthor ->
                visibleText.forEach { char ->
                    Text(
                        text = char.toString(),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                        ),
                        textAlign = textAlign,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                if (showAuthor && visibleAuthor.isNotBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = visibleAuthor,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
