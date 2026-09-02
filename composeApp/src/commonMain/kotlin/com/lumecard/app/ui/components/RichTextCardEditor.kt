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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.i18n.I18nManager
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import org.koin.compose.koinInject
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import kotlinx.coroutines.delay

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
    horizontalCenter: Boolean = false,
    verticalCenter: Boolean = false,
    onHorizontalCenterChange: ((Boolean) -> Unit)? = null,
    onVerticalCenterChange: ((Boolean) -> Unit)? = null,
) {
    val strings = koinInject<I18nManager>().strings
    Text(frontLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    RichEditField(initialHtml = front, onHtmlChange = onFrontChange)
    Spacer(Modifier.height(8.dp))
    Text(backLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    RichEditField(initialHtml = back, onHtmlChange = onBackChange)
    // 卡片级居中开关（与基础卡 BasicCardFields 同款），让富文本卡也能设置水平/垂直居中
    if (onHorizontalCenterChange != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.cardHorizontalCenter, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = horizontalCenter, onCheckedChange = onHorizontalCenterChange)
        }
    }
    if (onVerticalCenterChange != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.cardVerticalCenter, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = verticalCenter, onCheckedChange = onVerticalCenterChange)
        }
    }
}

@Composable
private fun RichEditField(initialHtml: String, onHtmlChange: (String) -> Unit) {
    val state = rememberRichTextState()
    var showColorMenu by remember { mutableStateOf(false) }
    var showFontSizeMenu by remember { mutableStateOf(false) }

    // 初始导入 HTML（仅首次组合；后续编辑由 state 自行管理，避免循环重置选区）
    LaunchedEffect(Unit) {
        state.setHtml(initialHtml)
    }

    // 编辑变化 → 防抖导出 HTML
    LaunchedEffect(state.annotatedString) {
        delay(200)
        val html = state.toHtml()
        // Strip empty HTML artifacts (e.g. "<p></p>", "<p><br></p>") so that
        // switching away from RICH_TEXT type doesn't leave ghost tags in the
        // front/back fields.
        val stripped = html
            .replace(Regex("<p>\\s*</p>"), "")
            .replace(Regex("<p><br\\s*/?>\\s*</p>"), "")
            .trim()
        onHtmlChange(stripped)
    }

    val currentStyle = state.currentSpanStyle
    val isBold = currentStyle.fontWeight == FontWeight.Bold
    val isItalic = currentStyle.fontStyle == FontStyle.Italic
    val isUnderline = currentStyle.textDecoration == TextDecoration.Underline

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }, selected = isBold, icon = { Text("B", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) })
            ToolbarButton(onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }, selected = isItalic, icon = { Text("I", fontStyle = FontStyle.Italic, fontSize = 14.sp) })
            ToolbarButton(onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }, selected = isUnderline, icon = { Text("U", fontSize = 14.sp) })

            Spacer(Modifier.width(4.dp))
            Box {
                ToolbarButton(onClick = { showColorMenu = true }, selected = false, icon = { Text("\uD83C\uDFA8", fontSize = 16.sp) })
                DropdownMenu(expanded = showColorMenu, onDismissRequest = { showColorMenu = false }) {
                    presetColors.forEachIndexed { index, color ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (color != null) {
                                        Box(Modifier.size(16.dp).background(color, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                    } else {
                                        Box(Modifier.size(16.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                    }
                                    Text(presetColorNames[index])
                                }
                            },
                            onClick = {
                                if (color != null) {
                                    state.toggleSpanStyle(SpanStyle(color = color))
                                } else {
                                    val cur = state.currentSpanStyle.color
                                    if (cur != Color.Unspecified) {
                                        state.toggleSpanStyle(SpanStyle(color = cur))
                                    }
                                }
                                showColorMenu = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))
            Box {
                ToolbarButton(onClick = { showFontSizeMenu = true }, selected = false, icon = { Text("A", fontSize = 12.sp) })
                DropdownMenu(expanded = showFontSizeMenu, onDismissRequest = { showFontSizeMenu = false }) {
                    presetFontSizes.forEach { size ->
                        DropdownMenuItem(
                            text = { Text("${size}px") },
                            onClick = { state.toggleSpanStyle(SpanStyle(fontSize = size.sp)); showFontSizeMenu = false },
                        )
                    }
                }
            }
        }

        RichTextEditor(
            state = state,
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun ToolbarButton(onClick: () -> Unit, selected: Boolean, icon: @Composable () -> Unit) {
    // 注意：不能使用 Surface(onClick)/Button，它们点击时会抢占焦点，导致编辑器失去焦点、
    // selection 被折叠，进而 toggleSpanStyle 无法作用于选中的文字。这里用 clickable +
    // focusProperties{canFocus=false} 让按钮不抢焦点。关键：focusProperties 必须写在
    // clickable 之前（外层），因为它修饰的是「它下面第一个 focusTarget」；写反则无效。
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
