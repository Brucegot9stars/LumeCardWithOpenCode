package com.lumecard.app.ui.screens.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.ui.components.MarkdownText
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.app.ui.screens.settings.AnswerDisplayMode
import org.koin.compose.koinInject

private const val FLIP_DURATION_MS = 500
@Composable
internal fun CardContent(
    card: Card,
    isFlipped: Boolean,
    displayMode: AnswerDisplayMode,
    onConfirmChoice: (() -> Unit)? = null,
) {
    val strings = koinInject<I18nManager>().strings
    Column(modifier = Modifier.fillMaxWidth()) {
        when (displayMode) {
            AnswerDisplayMode.FLIP -> {
                FlipCard(
                    isFlipped = isFlipped,
                    front = { CardFace(card, showBack = false, onConfirmChoice = onConfirmChoice) },
                    back = { CardFace(card, showBack = true) }
                )
            }
            AnswerDisplayMode.SPLIT -> {
                if (isFlipped) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    strings.studyQuestion,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            Spacer(Modifier.weight(1f))
                            Text(
                                cardTypeName(card.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ) {
                        CardFace(card, showBack = false, onConfirmChoice = onConfirmChoice).let { it }
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    strings.studyAnswer,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            Spacer(Modifier.weight(1f))
                            Text(
                                    strings.studyRevealed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    ) {
                        CardFace(card, showBack = true).let { it }
                    }
                } else {
                    CardFace(card, showBack = false, onConfirmChoice = onConfirmChoice)
                }
            }
        }
    }
}

@Composable
internal fun FlipCard(
    isFlipped: Boolean,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation = remember { Animatable(if (isFlipped) 180f else 0f) }
    var cardWidth by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(isFlipped) {
        rotation.animateTo(
            targetValue = if (isFlipped) 180f else 0f,
            animationSpec = tween(FLIP_DURATION_MS)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { cardWidth = it.width.coerceAtLeast(1).toFloat() }
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = cardWidth * 8f
            }
    ) {
        if (rotation.value < 90f) {
            front()
        } else {
            Box(modifier = Modifier.graphicsLayer { scaleX = -1f }) {
                back()
            }
        }
    }
}

@Composable
internal fun CardFace(
    card: Card,
    showBack: Boolean,
    onConfirmChoice: (() -> Unit)? = null,
) {
    val clozeRegex = remember { Regex("\\{\\{c\\d+::([^}]+)\\}\\}") }
    val clozeHintRegex = remember { Regex("\\{\\{c\\d+::([^}]+)::([^}]+)\\}\\}") }
    val clozeAnswerRegex = remember { Regex("\\{\\{c\\d+::([^:}]+)(?:::([^}]+))?\\}\\}") }

    val strings = koinInject<I18nManager>().strings
    Column(modifier = Modifier.fillMaxWidth()) {
        when (card.type) {
            CardType.BASIC -> {
                MarkdownText(
                    markdown = if (showBack) card.back else card.front,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CardType.RICH_TEXT -> {
                val html = if (showBack) card.back else card.front
                RichTextCardFace(html = html)
            }
            CardType.REVERSED, CardType.MARKDOWN, CardType.AI_GENERATED -> {
                MarkdownText(
                    markdown = if (showBack) card.back else card.front,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CardType.CLOZE -> {
                if (!showBack) {
                    val displayText = card.front.replace(clozeHintRegex, "____").replace(clozeRegex, "____")
                    Text(displayText, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(strings.studyClozeHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val annotated = buildAnnotatedString {
                        var pos = 0
                        for (match in clozeAnswerRegex.findAll(card.front)) {
                            if (pos < match.range.first) {
                                append(card.front.substring(pos, match.range.first))
                            }
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color.Red)) {
                                append(match.groupValues[1])
                            }
                            pos = match.range.last + 1
                        }
                        if (pos < card.front.length) {
                            append(card.front.substring(pos))
                        }
                    }
                    Text(annotated, style = MaterialTheme.typography.headlineSmall)
                }
            }
            CardType.MULTIPLE_CHOICE -> {
                val question = card.front
                val options = remember { card.back.split("\n").filter { it.isNotBlank() } }
                val cleanOptions = remember { options.map { it.removePrefix("+").trim() } }
                val correctIndices = remember {
                    options.mapIndexedNotNull { i, opt -> if (opt.startsWith("+")) i else null }.toSet()
                }
                Text(question, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (!showBack) {
                    var selectedOptions by remember { mutableStateOf(setOf<Int>()) }
                    cleanOptions.forEachIndexed { idx, displayOpt ->
                        val isSelected = idx in selectedOptions
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedOptions = if (isSelected) selectedOptions - idx
                                else selectedOptions + idx
                            },
                            label = { Text(displayOpt) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onConfirmChoice?.invoke() },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = selectedOptions.isNotEmpty(),
                    ) {
                        Text(strings.studyShowAnswer)
                    }
                } else {
                    cleanOptions.forEachIndexed { idx, displayOpt ->
                        val isCorrect = idx in correctIndices
                        val indicator = if (isCorrect) "\u2713 " else ""
                        val color = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(indicator, fontWeight = FontWeight.Bold, color = color)
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = isCorrect,
                                onClick = {},
                                label = {
                                    Text(displayOpt, fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal, color = color)
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE8F5E9),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun cardTypeName(type: CardType): String {
    val strings = koinInject<I18nManager>().strings
    return when (type) {
        CardType.BASIC -> strings.studyCardTypeBasic
        CardType.REVERSED -> strings.studyCardTypeReversed
        CardType.CLOZE -> strings.studyCardTypeCloze
        CardType.MULTIPLE_CHOICE -> strings.studyCardTypeChoice
        CardType.MARKDOWN -> strings.studyCardTypeMarkdown
        CardType.AI_GENERATED -> strings.studyCardTypeAi
        CardType.RICH_TEXT -> strings.studyCardTypeRichText
    }
}

@Composable
internal fun RichTextCardFace(html: String) {
    if (html.isBlank()) return
    val displayHtml = remember(html) {
        if (html.contains("<") && html.contains(">")) html
        else "<p>${html.replace("\n", "<br>")}</p>"
    }
    val state = rememberRichTextState()
    LaunchedEffect(displayHtml) {
        if (displayHtml.isNotBlank()) state.setHtml(displayHtml)
    }
    RichText(
        state = state,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun CompletionStat(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatElapsedTime(totalSeconds: Int, strings: I18nStrings): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            StringBuilder().append(hours).append(strings.timeHours).append(mins).append(strings.timeMinutes).toString()
        } else {
            StringBuilder().append(minutes).append(strings.timeMinutes).append(seconds).append(strings.timeSeconds).toString()
        }
    } else {
        StringBuilder().append(seconds).append(strings.timeSeconds).toString()
    }
}
