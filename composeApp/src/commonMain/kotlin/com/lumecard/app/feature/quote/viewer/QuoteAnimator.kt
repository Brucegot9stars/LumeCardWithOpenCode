package com.lumecard.app.feature.quote.viewer

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                val charDelay = when {
                    text.length > 50 -> 20L
                    text.length > 20 -> 40L
                    else -> 60L
                }
                for (i in 1..text.length) {
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
                val sentences = text.split(Regex("(?<=[。！？.!?\\n])"))
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

    content(visibleText, visibleAuthor)
}