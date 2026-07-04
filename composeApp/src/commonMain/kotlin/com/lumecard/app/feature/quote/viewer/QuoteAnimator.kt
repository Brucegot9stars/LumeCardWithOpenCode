package com.lumecard.app.feature.quote.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumecard.shared.feature.quote.config.QuoteAnimationStyle
import kotlinx.coroutines.delay

@Composable
fun AnimatedQuoteContent(
    text: String,
    author: String,
    animationStyle: QuoteAnimationStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (visibleText: String, visibleAuthor: String) -> Unit,
) {
    var visibleChars by remember { mutableStateOf(if (!enabled || animationStyle == QuoteAnimationStyle.NONE) text.length else 0) }
    var showFull by remember { mutableStateOf(!enabled || animationStyle == QuoteAnimationStyle.NONE) }

    LaunchedEffect(text, animationStyle, enabled) {
        if (!enabled || animationStyle == QuoteAnimationStyle.NONE) {
            visibleChars = text.length
            showFull = true
            return@LaunchedEffect
        }
        visibleChars = 0
        showFull = false
        when (animationStyle) {
            QuoteAnimationStyle.TYPEWRITER -> {
                for (i in 1..text.length) {
                    visibleChars = i
                    delay(30L)
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
                val sentences = text.split(Regex("(?<=[。！？.!?\\n])"))
                for (i in 1..sentences.size) {
                    visibleChars = sentences.take(i).joinToString("").length
                    delay(500L)
                }
                showFull = true
            }
            QuoteAnimationStyle.NONE -> {
                showFull = true
            }
        }
    }

    val visibleText = when {
        !enabled || animationStyle == QuoteAnimationStyle.NONE -> text
        animationStyle == QuoteAnimationStyle.TYPEWRITER || animationStyle == QuoteAnimationStyle.SENTENCE_BY_SENTENCE ->
            text.substring(0, visibleChars.coerceAtMost(text.length))
        else -> text
    }

    val visibleAuthor = when {
        animationStyle == QuoteAnimationStyle.TYPEWRITER && author.length > 0 -> {
            val progress = visibleChars.toFloat() / text.length.coerceAtLeast(1)
            val authorChars = (author.length * progress).toInt().coerceIn(0, author.length)
            author.substring(0, authorChars)
        }
        else -> if (showFull) author else ""
    }

    val animModifier = when (animationStyle) {
        QuoteAnimationStyle.FADE_IN -> {
            val alpha by animateFloatAsState(
                targetValue = if (showFull) 1f else 0f,
                animationSpec = tween(500),
            )
            modifier
        }
        QuoteAnimationStyle.SLIDE_UP -> {
            val offsetY by animateDpAsState(
                targetValue = if (showFull) 0.dp else 20.dp,
                animationSpec = tween(400),
            )
            modifier
        }
        else -> modifier
    }

    content(visibleText, visibleAuthor)
}
