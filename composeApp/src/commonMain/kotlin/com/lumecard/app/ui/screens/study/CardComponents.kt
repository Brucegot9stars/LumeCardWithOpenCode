package com.lumecard.app.ui.screens.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.ui.components.MarkdownText
import com.mohamedrejeb.richeditor.model.rememberRichTextState
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
    horizontalCenter: Boolean = false,
    verticalCenter: Boolean = false,
    fontSize: Int = 16,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    onConfirmChoice: (() -> Unit)? = null,
) {
    val strings = koinInject<I18nManager>().strings
    Column(modifier = Modifier.fillMaxWidth()) {
        when (displayMode) {
            AnswerDisplayMode.FLIP -> {
                key(card.id) {
                    FlipCard(
                        isFlipped = isFlipped,
                        front = { CardFace(card, showBack = false, onConfirmChoice = onConfirmChoice, horizontalCenter = horizontalCenter, fontSize = fontSize, fontFamily = fontFamily) },
                        back = { CardFace(card, showBack = true, horizontalCenter = horizontalCenter, fontSize = fontSize, fontFamily = fontFamily) }
                    )
                }
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
                        CardFace(card, showBack = false, onConfirmChoice = onConfirmChoice, horizontalCenter = horizontalCenter, fontSize = fontSize, fontFamily = fontFamily)
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
                        CardFace(card, showBack = true, horizontalCenter = horizontalCenter, fontSize = fontSize, fontFamily = fontFamily)
                    }
                } else {
                    CardFace(card, showBack = false, onConfirmChoice = onConfirmChoice, horizontalCenter = horizontalCenter, fontSize = fontSize, fontFamily = fontFamily)
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
    horizontalCenter: Boolean = false,
    fontSize: Int = 16,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
) {
    val clozeRegex = remember { Regex("\\{\\{c\\d+::([^}]+)\\}\\}") }
    val clozeHintRegex = remember { Regex("\\{\\{c\\d+::([^}]+)::([^}]+)\\}\\}") }

    val strings = koinInject<I18nManager>().strings
    val align = if (horizontalCenter) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (horizontalCenter) TextAlign.Center else TextAlign.Start
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        when (card.type) {
            CardType.BASIC, CardType.REVERSED -> {
                val fontId = card.metadata["fontFamily"] ?: ""
                val ff = fontFamily ?: com.lumecard.app.font.FontRegistry.resolveFontFamily(
                    if (fontId.isNotBlank()) fontId else com.lumecard.app.font.FontRegistry.defaultFontId
                )
                Text(
                    text = if (showBack) card.back else card.front,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = textAlign,
                    fontSize = fontSize.sp,
                    fontFamily = ff,
                )
            }
            CardType.RICH_TEXT -> {
                val html = if (showBack) card.back else card.front
                RichTextCardFace(html = html, horizontalCenter = horizontalCenter)
            }
            CardType.MARKDOWN, CardType.AI_GENERATED -> {
                MarkdownText(
                    markdown = if (showBack) card.back else card.front,
                    modifier = Modifier.fillMaxWidth(),
                    center = true,
                )
            }
            CardType.CLOZE -> {
                if (!showBack) {
                    val displayText = remember(card.front) { card.front.replace(clozeHintRegex, "____").replace(clozeRegex, "____") }
                    Text(displayText, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = textAlign, fontSize = fontSize.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(strings.studyClozeHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                } else {
                    val annotated = buildAnnotatedString {
                        var pos = 0
                        for (match in clozeRegex.findAll(card.front)) {
                            if (pos < match.range.first) {
                                append(card.front.substring(pos, match.range.first))
                            }
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color.Red)) {
                                append(match.groupValues[1].substringBefore("::"))
                            }
                            pos = match.range.last + 1
                        }
                        if (pos < card.front.length) {
                            append(card.front.substring(pos))
                        }
                    }
                    Text(annotated, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = textAlign, fontSize = fontSize.sp)
                }
            }
            CardType.MULTIPLE_CHOICE -> {
                val question = card.front
                val options = remember(card.back) { card.back.split("\n").filter { it.isNotBlank() } }
                val cleanOptions = remember(options) { options.map { it.removePrefix("+").trim() } }
                val correctIndices = remember(options) {
                    options.mapIndexedNotNull { i, opt -> if (opt.startsWith("+")) i else null }.toSet()
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            question,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
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
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
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
                                            Text(displayOpt, fontWeight = FontWeight.Bold, color = color)
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
internal fun RichTextCardFace(html: String, horizontalCenter: Boolean = false) {
    if (html.isBlank()) return
    // 用 Compose Rich Editor 的 setHtml 解析 HTML 得到 AnnotatedString（含加粗/颜色/字号等 SpanStyle），
    // 但渲染改用 compose 原生 BasicText —— 绕开 richeditor 1.0.0 的 BasicRichText 渲染 bug
    // （它在渲染 colorSpace=Uninitialized 的 Color 时当成透明，导致无显式颜色的文字不可见）。
    val state = rememberRichTextState()
    LaunchedEffect(html) {
        state.setHtml(html)
    }
    // setHtml 对「无显式 color」的文字会写入一个完全透明（alpha=0）的 Color，
    // BasicText 把它当成透明 → 不可见；而显式设了颜色的（如红色）正常。
    // 这里把 alpha==0 的 span 颜色替换为 Color.Unspecified，让 BasicText 正确
    // 回退到样式基础色，加粗/颜色/字号等 SpanStyle 正常显示。
    val annotated = remember(state.annotatedString, horizontalCenter) {
        val src = state.annotatedString
        val builder = AnnotatedString.Builder()
        builder.append(src.text)
        for (range in src.spanStyles) {
            if (range.start >= range.end) continue
            val item = range.item
            val fixed = if (item.color.alpha == 0f) {
                item.copy(color = Color.Unspecified)
            } else item
            builder.addStyle(fixed, range.start, range.end)
        }
        // 段落样式（如标题对齐/行高）一并保留，避免 h1-h6 等结构信息丢失。
        // 若开启了卡片级水平居中，则强制覆盖每个段落的对齐为 Center，
        // 保证「水平居中」开关对所有段落（含 HTML 内单独设了左对齐的）都生效。
        for (range in src.paragraphStyles) {
            if (range.start >= range.end) continue
            val item = if (horizontalCenter) range.item.copy(textAlign = TextAlign.Center) else range.item
            builder.addStyle(item, range.start, range.end)
        }
        builder.toAnnotatedString()
    }
    BasicText(
        text = annotated,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge.copy(
            textAlign = if (horizontalCenter) TextAlign.Center else TextAlign.Unspecified
        ),
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
