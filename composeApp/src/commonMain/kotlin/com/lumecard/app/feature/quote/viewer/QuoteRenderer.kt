package com.lumecard.app.feature.quote.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.shared.data.*
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.platform.platformLoadImage

sealed class ResolvedBackground {
    data object Default : ResolvedBackground()
    data class SolidColor(val color: Color) : ResolvedBackground()
    data class Image(val painter: Painter) : ResolvedBackground()
}

@Composable
fun QuoteBackgroundBox(
    background: ResolvedBackground,
    modifier: Modifier = Modifier,
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
                androidx.compose.foundation.Image(
                    painter = background.painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
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

private fun tryLoadImage(path: String): Painter? = platformLoadImage(path)

fun resolveBackground(
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

@Composable
fun QuoteTextLayout(
    text: String,
    author: String,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    showAuthor: Boolean,
    layoutOverride: QuoteLayoutOverride?,
    modifier: Modifier = Modifier,
) {
    val textAlign = resolveTextAlign(layoutOverride?.textAlign, ComposeTextAlign.Center)
    val authorAlign = resolveTextAlign(layoutOverride?.authorAlign, ComposeTextAlign.Center)
    val contentSpacing = layoutOverride?.contentSpacing?.dp ?: 24.dp
    val lineSpacing = (contentSpacing / 3).coerceAtLeast(4.dp)
    val paddingHorizontal = layoutOverride?.pagePadding?.dp ?: 32.dp
    val maxWidthFraction = layoutOverride?.maxWidth
    val verticalArrangement = when (layoutOverride?.verticalPosition ?: VerticalPosition.CENTER) {
        VerticalPosition.TOP -> Arrangement.Top
        VerticalPosition.CENTER -> Arrangement.Center
        VerticalPosition.BOTTOM -> Arrangement.Bottom
    }

    val lines = remember(text) { text.splitLines() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = paddingHorizontal),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = if (maxWidthFraction != null) {
                Modifier.fillMaxWidth(maxWidthFraction)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        textAlign = textAlign,
                    ),
                )
                if (index < lines.lastIndex) {
                    Spacer(Modifier.height(lineSpacing))
                }
            }
        }
        if (showAuthor) {
            Spacer(Modifier.height(contentSpacing))
            val authorLines = author.split("\n")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                authorLines.forEachIndexed { idx, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = fontFamily,
                            textAlign = authorAlign,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (idx < authorLines.lastIndex) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

private fun String.splitLines(): List<String> = split("\n")

internal fun resolveTextAlign(override: TextAlignOverride?, default: ComposeTextAlign): ComposeTextAlign {
    return when (override) {
        TextAlignOverride.START -> ComposeTextAlign.Start
        TextAlignOverride.CENTER -> ComposeTextAlign.Center
        TextAlignOverride.END -> ComposeTextAlign.End
        null -> default
    }
}
