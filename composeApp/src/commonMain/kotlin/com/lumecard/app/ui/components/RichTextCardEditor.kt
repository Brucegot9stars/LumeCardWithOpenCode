package com.lumecard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import com.lumecard.app.i18n.I18nManager
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.sample
import org.koin.compose.koinInject

private val presetColors = listOf(
    null,
    Color(0xFFD32F2F),
    Color(0xFF1976D2),
    Color(0xFF388E3C),
    Color(0xFFF57C00),
    Color(0xFF7B1FA2),
    Color(0xFF00796B),
    Color(0xFF5D4037),
    Color(0xFF455A64),
)

private val presetColorNames = listOf(
    "none",
    "red",
    "blue",
    "green",
    "orange",
    "purple",
    "teal",
    "brown",
    "gray",
)

private val presetFontSizes = listOf(12, 14, 16, 18, 20, 24, 30, 36)

@Composable
fun RichTextCardEditor(
    front: String,
    onFrontChange: (String) -> Unit,
    back: String,
    onBackChange: (String) -> Unit,
    frontLabel: String,
    backLabel: String,
    onStatesReady: ((frontState: RichTextState, backState: RichTextState) -> Unit)? = null,
) {
    val strings = koinInject<I18nManager>().strings
    val scope = rememberCoroutineScope()

    val frontState = rememberRichTextState()
    val backState = rememberRichTextState()

    LaunchedEffect(Unit) {
        onStatesReady?.invoke(frontState, backState)
    }

    val frontHtml = remember(front) {
        if (front.contains("<") && front.contains(">")) front
        else "<p>${front.replace("\n", "<br>")}</p>"
    }
    val backHtml = remember(back) {
        if (back.contains("<") && back.contains(">")) back
        else "<p>${back.replace("\n", "<br>")}</p>"
    }
    LaunchedEffect(Unit) {
        if (frontHtml.isNotBlank()) frontState.setHtml(frontHtml)
        if (backHtml.isNotBlank()) backState.setHtml(backHtml)
    }

    LaunchedEffect(frontState) {
        snapshotFlow { frontState.toHtml() }
            .sample(300)
            .collectLatest { html -> onFrontChange(html) }
    }
    LaunchedEffect(backState) {
        snapshotFlow { backState.toHtml() }
            .sample(300)
            .collectLatest { html -> onBackChange(html) }
    }

    Text(frontLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    RichTextToolbar(state = frontState)
    RichTextEditor(
        state = frontState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
    )

    Spacer(Modifier.height(8.dp))
    Text(backLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    RichTextToolbar(state = backState)
    RichTextEditor(
        state = backState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
    )
}

@Composable
private fun RichTextToolbar(
    state: RichTextState,
) {
    val strings = koinInject<I18nManager>().strings
    val currentStyle = state.currentSpanStyle
    val isBold = currentStyle.fontWeight == FontWeight.Bold
    val isItalic = currentStyle.fontStyle == FontStyle.Italic
    val isUnderline = currentStyle.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline

    var showColorPicker by remember { mutableStateOf(false) }
    var showFontSizeMenu by remember { mutableStateOf(false) }
    var savedSelection by remember { mutableStateOf(TextRange(0)) }

    fun saveSelection() {
        savedSelection = state.selection
    }

    fun hasStyleInSelection(check: (SpanStyle) -> Boolean): Boolean {
        if (savedSelection.collapsed) return false
        val rangeStyle = state.getSpanStyle(savedSelection)
        return check(rangeStyle)
    }

    fun toggleStyleOnSelection(style: SpanStyle, check: (SpanStyle) -> Boolean) {
        state.selection = savedSelection
        if (hasStyleInSelection(check)) {
            state.removeSpanStyle(style, savedSelection)
        } else {
            state.addSpanStyle(style, savedSelection)
        }
    }

    fun applyStyleOnSelection(style: SpanStyle) {
        state.selection = savedSelection
        state.addSpanStyle(style, savedSelection)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(
            onClick = {
                saveSelection()
                toggleStyleOnSelection(
                    SpanStyle(fontWeight = FontWeight.Bold)
                ) { it.fontWeight == FontWeight.Bold }
            },
            selected = isBold,
            icon = { Text("B", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) },
        )
        ToolbarButton(
            onClick = {
                saveSelection()
                toggleStyleOnSelection(
                    SpanStyle(fontStyle = FontStyle.Italic)
                ) { it.fontStyle == FontStyle.Italic }
            },
            selected = isItalic,
            icon = { Text("I", fontStyle = FontStyle.Italic, fontSize = 14.sp) },
        )
        ToolbarButton(
            onClick = {
                saveSelection()
                toggleStyleOnSelection(
                    SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                ) { it.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline }
            },
            selected = isUnderline,
            icon = { Text("U", fontSize = 14.sp) },
        )

        Spacer(Modifier.width(4.dp))
        Box {
            ToolbarButton(
                onClick = {
                    saveSelection()
                    showColorPicker = true
                },
                selected = false,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                currentStyle.color ?: MaterialTheme.colorScheme.onSurface,
                                CircleShape
                            )
                            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)
                    )
                },
            )
            DropdownMenu(
                expanded = showColorPicker,
                onDismissRequest = { showColorPicker = false },
            ) {
                Text(strings.editorColorTitle, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                presetColors.forEachIndexed { index, color ->
                    val c = color
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .then(
                                            if (c != null) Modifier.background(c, CircleShape)
                                            else Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                                        )
                                        .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (c == null) {
                                        Text("\u2715", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    when (presetColorNames[index]) {
                                        "none" -> strings.editorColorNone
                                        "red" -> strings.editorColorRed
                                        "blue" -> strings.editorColorBlue
                                        "green" -> strings.editorColorGreen
                                        "orange" -> strings.editorColorOrange
                                        "purple" -> strings.editorColorPurple
                                        "teal" -> strings.editorColorTeal
                                        "brown" -> strings.editorColorBrown
                                        "gray" -> strings.editorColorGray
                                        else -> strings.editorColorCustom
                                    },
                                    fontSize = 14.sp,
                                )
                            }
                        },
                        onClick = {
                            state.selection = savedSelection
                            if (c != null) {
                                state.addSpanStyle(SpanStyle(color = c), savedSelection)
                            } else {
                                state.removeSpanStyle(SpanStyle(color = Color.Unspecified), savedSelection)
                            }
                            showColorPicker = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))
        Box {
            ToolbarButton(
                onClick = {
                    saveSelection()
                    showFontSizeMenu = true
                },
                selected = false,
                icon = {
                    Text(
                        "${(currentStyle.fontSize?.value ?: 16f).toInt()}",
                        fontSize = 11.sp
                    )
                },
            )
            DropdownMenu(
                expanded = showFontSizeMenu,
                onDismissRequest = { showFontSizeMenu = false },
            ) {
                presetFontSizes.forEach { size ->
                    DropdownMenuItem(
                        text = { Text(strings.editorFontSizePx(size), fontSize = 13.sp) },
                        onClick = {
                            applyStyleOnSelection(SpanStyle(fontSize = size.sp))
                            showFontSizeMenu = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        ToolbarButton(
            onClick = { /* undo */ },
            selected = false,
            icon = { Text("\u21A9", fontSize = 16.sp) },
        )
        ToolbarButton(
            onClick = { /* redo */ },
            selected = false,
            icon = { Text("\u21AA", fontSize = 16.sp) },
        )
    }
}

@Composable
private fun ToolbarButton(
    onClick: () -> Unit,
    selected: Boolean,
    icon: @Composable () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else Color.Transparent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
