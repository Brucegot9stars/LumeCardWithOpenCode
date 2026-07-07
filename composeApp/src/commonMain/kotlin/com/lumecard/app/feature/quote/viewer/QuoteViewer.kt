package com.lumecard.app.feature.quote.viewer

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lumecard.app.font.FontRegistry
import com.lumecard.shared.data.*
import com.lumecard.shared.feature.quote.config.QuoteAnimationStyle
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

    var animAlpha by remember { mutableFloatStateOf(if (config.enableAnimation && config.animationStyle == QuoteAnimationStyle.FADE_IN) 0f else 1f) }
    var animOffsetY by remember { mutableFloatStateOf(if (config.enableAnimation && config.animationStyle == QuoteAnimationStyle.SLIDE_UP) 20f else 0f) }

    LaunchedEffect(config.enableAnimation, config.animationStyle) {
        if (!config.enableAnimation || config.animationStyle == QuoteAnimationStyle.NONE ||
            config.animationStyle == QuoteAnimationStyle.TYPEWRITER ||
            config.animationStyle == QuoteAnimationStyle.SENTENCE_BY_SENTENCE
        ) {
            animAlpha = 1f
            animOffsetY = 0f
            return@LaunchedEffect
        }
        when (config.animationStyle) {
            QuoteAnimationStyle.FADE_IN -> {
                animAlpha = 0f
                delay(50L)
                val steps = 30
                val stepMs = 30L
                for (i in 1..steps) {
                    animAlpha = i.toFloat() / steps
                    delay(stepMs)
                }
                animAlpha = 1f
            }
            QuoteAnimationStyle.SLIDE_UP -> {
                animOffsetY = 30f
                delay(50L)
                val steps = 20
                val stepMs = 25L
                for (i in 1..steps) {
                    animOffsetY = 30f * (steps - i).toFloat() / steps
                    delay(stepMs)
                }
                animOffsetY = 0f
            }
            else -> {}
        }
    }

    val animModifier = if (config.enableAnimation) {
        when (config.animationStyle) {
            QuoteAnimationStyle.FADE_IN -> Modifier.alpha(animAlpha)
            QuoteAnimationStyle.SLIDE_UP -> Modifier.offset(y = animOffsetY.dp)
            else -> Modifier
        }
    } else Modifier

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
            .then(tapModifier)
            .then(animModifier),
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
    val contentSpacing = layoutOverride?.contentSpacing?.dp ?: 24.dp
    val textAlign = resolveTextAlign(layoutOverride?.textAlign, ComposeTextAlign.Center)

    var visibleChars by remember { mutableStateOf(if (!config.enableAnimation || config.animationStyle == QuoteAnimationStyle.NONE) quote.text.length else 0) }
    var showFull by remember { mutableStateOf(!config.enableAnimation || config.animationStyle == QuoteAnimationStyle.NONE) }

    LaunchedEffect(quote.text, config.animationStyle, config.enableAnimation) {
        if (!config.enableAnimation || config.animationStyle == QuoteAnimationStyle.NONE) {
            visibleChars = quote.text.length
            showFull = true
            return@LaunchedEffect
        }
        visibleChars = 0
        showFull = false
        when (config.animationStyle) {
            QuoteAnimationStyle.TYPEWRITER -> {
                val charDelay = when {
                    quote.text.length > 50 -> 20L
                    quote.text.length > 20 -> 40L
                    else -> 60L
                }
                for (i in 1..quote.text.length) {
                    visibleChars = i
                    delay(charDelay)
                }
                showFull = true
            }
            QuoteAnimationStyle.FADE_IN -> {
                delay(100L)
                showFull = true
            }
            QuoteAnimationStyle.SLIDE_UP -> {
                delay(100L)
                showFull = true
            }
            QuoteAnimationStyle.SENTENCE_BY_SENTENCE -> {
                val sentences = quote.text.split(Regex("(?<=[。！？.!?\\n])"))
                val sentenceDelay = when {
                    sentences.size <= 1 -> 800L
                    else -> 600L
                }
                for (i in 1..sentences.size) {
                    visibleChars = sentences.take(i).joinToString("").length
                    delay(sentenceDelay)
                }
                showFull = true
            }
            QuoteAnimationStyle.NONE -> {
                showFull = true
            }
        }
    }

    val visibleText = when {
        !config.enableAnimation || config.animationStyle == QuoteAnimationStyle.NONE -> quote.text
        config.animationStyle == QuoteAnimationStyle.TYPEWRITER || config.animationStyle == QuoteAnimationStyle.SENTENCE_BY_SENTENCE ->
            quote.text.substring(0, visibleChars.coerceAtMost(quote.text.length))
        else -> quote.text
    }

    val visibleAuthor = when {
        config.animationStyle == QuoteAnimationStyle.TYPEWRITER && quote.author.length > 0 -> {
            val progress = visibleChars.toFloat() / quote.text.length.coerceAtLeast(1)
            val authorChars = (quote.author.length * progress).toInt().coerceIn(0, quote.author.length)
            quote.author.substring(0, authorChars)
        }
        else -> if (showFull) quote.author else ""
    }

    val verticalArrangement = when (layoutOverride?.verticalPosition ?: VerticalPosition.CENTER) {
        VerticalPosition.TOP -> Arrangement.Top
        VerticalPosition.CENTER -> Arrangement.Center
        VerticalPosition.BOTTOM -> Arrangement.Bottom
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = paddingHorizontal),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val cols = visibleText.split("\n").reversed()
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            cols.forEachIndexed { colIdx, colText ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    colText.forEach { char ->
                        Text(
                            text = char.toVertical().toString(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = fontFamily,
                                fontSize = fontSize,
                            ),
                            textAlign = textAlign,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                if (colIdx < cols.lastIndex) {
                    Spacer(Modifier.width(32.dp))
                }
            }
        }
        if (showAuthor) {
            Spacer(Modifier.height(contentSpacing))
            val authorCols = visibleAuthor.split("\n").reversed()
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top,
            ) {
                authorCols.forEachIndexed { colIdx, colText ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        colText.forEach { char ->
                            Text(
                                text = char.toVertical().toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (colIdx < authorCols.lastIndex) {
                        Spacer(Modifier.width(32.dp))
                    }
                }
            }
        }
    }
}

private fun Char.toVertical(): Char = when (this) {
    '《' -> '﹁'
    '》' -> '﹂'
    else -> this
}