package com.lumecard.app.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.lumecard.app.platform.platformLoadImage
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.*
import kotlinx.coroutines.delay

@Composable
fun SplashQuoteScreen(
    quote: SplashQuoteData,
    direction: SplashQuoteDirection = SplashQuoteDirection.HORIZONTAL,
    splashFontId: String = "",
    splashFontSize: Float = 0f,
    durationMs: Long = 3000L,
    backgroundPath: String = "",
    showAuthor: Boolean = true,
    overrideConfig: QuoteOverrideConfig? = null,
    onDismiss: () -> Unit,
) {
    val effectiveDirection = overrideConfig?.direction?.resolveOverridden(direction) ?: direction
    val effectiveFontId = overrideConfig?.font?.resolveOverridden(splashFontId) ?: splashFontId
    val effectiveFontSize = overrideConfig?.fontSize?.resolveOverridden(splashFontSize) ?: splashFontSize
    val effectiveShowAuthor = overrideConfig?.showAuthor?.resolveOverridden(showAuthor) ?: showAuthor

    val effectiveBackground = resolveBackground(overrideConfig?.background, backgroundPath)

    val autoDismiss = remember { mutableStateOf(false) }

    LaunchedEffect(durationMs) {
        delay(durationMs)
        if (!autoDismiss.value) {
            autoDismiss.value = true
            onDismiss()
        }
    }

    val fontFamily = remember(effectiveFontId) {
        if (effectiveFontId.isNotBlank()) {
            FontRegistry.resolveFontFamily(effectiveFontId)
        } else {
            FontFamily.Default
        }
    }

    val fontSize: TextUnit = remember(effectiveFontSize) {
        if (effectiveFontSize > 0f) effectiveFontSize.sp else 24.sp
    }

    SplashBackgroundBox(
        background = effectiveBackground,
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                autoDismiss.value = true
                onDismiss()
            },
    ) {
        when (effectiveDirection) {
            SplashQuoteDirection.HORIZONTAL -> {
                HorizontalLayout(
                    quote = quote,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    showAuthor = effectiveShowAuthor,
                    layoutOverride = overrideConfig?.layout,
                )
            }
            SplashQuoteDirection.VERTICAL -> {
                VerticalLayout(
                    quote = quote,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    showAuthor = effectiveShowAuthor,
                    layoutOverride = overrideConfig?.layout,
                )
            }
        }
    }
}

@Composable
private fun SplashBackgroundBox(
    background: ResolvedBackground,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    when (background) {
        is ResolvedBackground.Default -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
        is ResolvedBackground.SolidColor -> {
            Box(
                modifier = modifier.background(background.color),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
        is ResolvedBackground.Image -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                // Background image layer
                androidx.compose.foundation.Image(
                    painter = background.painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Content on top
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
}

private sealed class ResolvedBackground {
    data object Default : ResolvedBackground()
    data class SolidColor(val color: Color) : ResolvedBackground()
    data class Image(val painter: androidx.compose.ui.graphics.painter.Painter) : ResolvedBackground()
}

private fun resolveBackground(
    bgOverride: BackgroundOverride?,
    globalBackgroundPath: String,
): ResolvedBackground {
    if (bgOverride != null) {
        when (bgOverride.type) {
            BackgroundType.SOLID_COLOR -> {
                val hex = bgOverride.color
                if (!hex.isNullOrBlank()) {
                    val color = tryParseHexColor(hex)
                    if (color != null) return ResolvedBackground.SolidColor(color)
                }
            }
            BackgroundType.IMAGE -> {
                val path = bgOverride.imagePath
                if (!path.isNullOrBlank()) {
                    val painter = tryLoadImage(path)
                    if (painter != null) return ResolvedBackground.Image(painter)
                }
            }
            BackgroundType.FOLLOW_GLOBAL -> {}
        }
    }
    if (globalBackgroundPath.isNotBlank()) {
        val painter = tryLoadImage(globalBackgroundPath)
        if (painter != null) return ResolvedBackground.Image(painter)
    }
    return ResolvedBackground.Default
}

private fun tryParseHexColor(hex: String): Color? {
    return try {
        val h = hex.removePrefix("#")
        val rgb = h.toLong(16)
        when (h.length) {
            6 -> Color(0xFF000000 or rgb)
            8 -> Color(rgb shl 24 or (rgb shr 8 and 0xFFFFFF))
            else -> null
        }
    } catch (_: Exception) { null }
}

private fun tryLoadImage(path: String): Painter? {
    return platformLoadImage(path)
}

@Composable
private fun HorizontalLayout(
    quote: SplashQuoteData,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    showAuthor: Boolean,
    layoutOverride: QuoteLayoutOverride?,
) {
    val textAlign = resolveTextAlign(layoutOverride?.textAlign, ComposeTextAlign.Center)
    val authorAlign = resolveTextAlign(layoutOverride?.authorAlign, ComposeTextAlign.Center)
    val contentSpacing = layoutOverride?.contentSpacing?.dp ?: 24.dp
    val paddingHorizontal = layoutOverride?.pagePadding?.dp ?: 32.dp
    val maxWidthFraction = layoutOverride?.maxWidth
    val verticalPos = layoutOverride?.verticalPosition ?: VerticalPosition.CENTER
    val verticalArrangement = when (verticalPos) {
        VerticalPosition.TOP -> Arrangement.Top
        VerticalPosition.CENTER -> Arrangement.Center
        VerticalPosition.BOTTOM -> Arrangement.Bottom
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = paddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = when (textAlign) {
                ComposeTextAlign.Start -> Alignment.Start
                ComposeTextAlign.Center -> Alignment.CenterHorizontally
                ComposeTextAlign.End -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
            verticalArrangement = verticalArrangement,
            modifier = Modifier
                .let { m ->
                    if (maxWidthFraction != null) m.fillMaxWidth(fraction = maxWidthFraction.coerceIn(0.1f, 1f))
                    else m
                },
        ) {
            Text(
                text = quote.text,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.6f,
                    textAlign = textAlign,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (showAuthor && quote.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(contentSpacing))
                Text(
                    text = "—— ${quote.author}",
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize * 0.75f,
                        textAlign = authorAlign,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun isCjkText(text: String): Boolean {
    if (text.isBlank()) return false
    var cjkCount = 0
    var totalCount = 0
    for (ch in text) {
        if (ch.isWhitespace()) continue
        totalCount++
        when (ch) {
            in '\u4E00'..'\u9FFF', in '\u3400'..'\u4DBF', in '\uF900'..'\uFAFF',
                in '\uFF01'..'\uFF60', in '\u3000'..'\u303F', '\u3005', '\u3006' -> cjkCount++
        }
    }
    return totalCount > 0 && cjkCount.toFloat() / totalCount > 0.5f
}

private fun splitSentences(text: String): List<String> {
    val sentences = mutableListOf<String>()
    val buf = StringBuilder()
    for (ch in text) {
        buf.append(ch)
        if (ch in "。！？；\n") {
            sentences.add(buf.toString())
            buf.clear()
        }
    }
    if (buf.isNotBlank()) sentences.add(buf.toString())
    return sentences
}

@Composable
private fun VerticalLayout(
    quote: SplashQuoteData,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    showAuthor: Boolean,
    layoutOverride: QuoteLayoutOverride?,
) {
    val isCjk = remember(quote.text) { isCjkText(quote.text) }
    val authorIsCjk = remember(quote.author) { isCjkText(quote.author) }
    val paddingHorizontal = layoutOverride?.pagePadding?.dp ?: 32.dp
    val maxWidthFraction = layoutOverride?.maxWidth
    val verticalPos = layoutOverride?.verticalPosition ?: VerticalPosition.CENTER

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = paddingHorizontal),
        contentAlignment = when (verticalPos) {
            VerticalPosition.TOP -> Alignment.TopCenter
            VerticalPosition.CENTER -> Alignment.Center
            VerticalPosition.BOTTOM -> Alignment.BottomCenter
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .let { m ->
                    if (maxWidthFraction != null) m.fillMaxWidth(fraction = maxWidthFraction.coerceIn(0.1f, 1f))
                    else m
                },
        ) {
            if (isCjk) {
                val sentences = remember(quote.text) { splitSentences(quote.text).reversed() }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        sentences.forEach { sentence ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                for (ch in sentence) {
                                    if (ch == ' ') continue
                                    Text(
                                        text = ch.toString(),
                                        style = TextStyle(
                                            fontFamily = fontFamily,
                                            fontSize = fontSize,
                                            lineHeight = fontSize * 0.95f,
                                            textAlign = ComposeTextAlign.Center,
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val words = remember(quote.text) {
                    quote.text.split(Regex("\\s+")).filter { it.isNotBlank() }
                }
                words.forEach { word ->
                    Text(
                        text = word,
                        style = TextStyle(
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            lineHeight = fontSize * 1.3f,
                            textAlign = ComposeTextAlign.Center,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            if (showAuthor && quote.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                for (ch in "│") {
                    Text(
                        text = ch.toString(),
                        style = TextStyle(
                            fontFamily = fontFamily,
                            fontSize = fontSize * 0.75f,
                            lineHeight = fontSize * 0.95f,
                            textAlign = ComposeTextAlign.Center,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                if (authorIsCjk) {
                    val authorSentences = remember(quote.author) { splitSentences(quote.author).reversed() }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            authorSentences.forEach { sentence ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                for (ch in sentence) {
                                    if (ch == ' ') continue
                                    Text(
                                        text = ch.toString(),
                                        style = TextStyle(
                                            fontFamily = fontFamily,
                                            fontSize = fontSize,
                                            lineHeight = fontSize * 0.95f,
                                            textAlign = ComposeTextAlign.Center,
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                        }
                    }
                } else {
                    val authorWords = remember(quote.author) {
                        quote.author.split(Regex("\\s+")).filter { it.isNotBlank() }
                    }
                    authorWords.forEach { word ->
                        Text(
                            text = word,
                            style = TextStyle(
                                fontFamily = fontFamily,
                                fontSize = fontSize,
                                lineHeight = fontSize * 1.3f,
                                textAlign = ComposeTextAlign.Center,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

private fun resolveTextAlign(override: TextAlignOverride?, default: ComposeTextAlign): ComposeTextAlign {
    return when (override) {
        TextAlignOverride.START -> ComposeTextAlign.Start
        TextAlignOverride.CENTER -> ComposeTextAlign.Center
        TextAlignOverride.END -> ComposeTextAlign.End
        null -> default
    }
}
