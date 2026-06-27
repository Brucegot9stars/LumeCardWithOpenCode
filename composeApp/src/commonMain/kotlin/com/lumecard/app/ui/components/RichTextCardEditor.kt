package com.lumecard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.ui.util.StyleSpan
import com.lumecard.app.ui.util.parseHtmlColor
import com.lumecard.app.ui.util.parseHtmlToSpans
import com.lumecard.app.i18n.I18nManager
import kotlinx.coroutines.delay
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

private fun buildAnnotated(text: String, spans: List<StyleSpan>) = buildAnnotatedString {
    append(text)
    for (s in spans) {
        val start = s.start.coerceIn(0, text.length)
        val end = s.end.coerceIn(0, text.length)
        if (start < end) addStyle(s.toSpanStyle(), start, end)
    }
}

private fun mapPos(pos: Int, oldLen: Int, newLen: Int, prefix: Int, suffix: Int): Int = when {
    pos <= prefix -> pos
    pos >= oldLen - suffix -> pos + (newLen - oldLen)
    else -> prefix
}

private fun mergeSpans(spans: List<StyleSpan>): List<StyleSpan> {
    if (spans.size < 2) return spans
    val result = mutableListOf<StyleSpan>()
    var cur = spans.first()
    for (next in spans.drop(1)) {
        if (cur.end >= next.start
            && cur.fontWeight == next.fontWeight
            && cur.fontStyle == next.fontStyle
            && cur.textDecoration == next.textDecoration
            && cur.color == next.color
            && cur.fontSize == next.fontSize
        ) {
            cur = cur.copy(end = maxOf(cur.end, next.end))
        } else {
            result.add(cur)
            cur = next
        }
    }
    result.add(cur)
    return result
}

private fun rebuildSpans(oldText: String, newText: String, oldSpans: List<StyleSpan>): List<StyleSpan> {
    if (oldText == newText) return oldSpans
    val prefix = oldText.commonPrefixWith(newText).length
    val suffix = oldText.commonSuffixWith(newText).length
    val oldLen = oldText.length
    val newLen = newText.length
    return oldSpans.mapNotNull { s ->
        val ns = mapPos(s.start, oldLen, newLen, prefix, suffix).coerceIn(0, newLen)
        val ne = mapPos(s.end, oldLen, newLen, prefix, suffix).coerceIn(0, newLen)
        if (ns >= ne) null else s.copy(start = ns, end = ne)
    }
}

private fun spansToHtml(text: String, spans: List<StyleSpan>): String {
    if (text.isEmpty()) return ""
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        val active = spans.filter { i in it.start until it.end }
        val combined = active.fold(SpanStyle()) { acc, s ->
            SpanStyle(
                fontWeight = s.fontWeight ?: acc.fontWeight,
                fontStyle = s.fontStyle ?: acc.fontStyle,
                textDecoration = s.textDecoration ?: acc.textDecoration,
                color = s.color ?: acc.color,
                fontSize = s.fontSize ?: acc.fontSize,
            )
        }
        val changes = (active.flatMap { listOf(it.start, it.end) } + text.length)
            .filter { it > i }.minOrNull() ?: text.length
        val segment = text.substring(i, changes)
        sb.append(styleToOpen(combined))
        sb.append(escapeHtml(segment))
        sb.append(styleToClose(combined))
        i = changes
    }
    return sb.toString()
}

private fun styleToOpen(s: SpanStyle): String {
    val t = mutableListOf<String>()
    if (s.fontWeight == FontWeight.Bold) t.add("<strong>")
    if (s.fontStyle == FontStyle.Italic) t.add("<em>")
    if (s.textDecoration == TextDecoration.Underline) t.add("<u>")
    val hasColor = s.color != Color.Unspecified
    val hasFontSize = s.fontSize != TextUnit.Unspecified
    if (hasColor || hasFontSize) {
        val attrs = mutableListOf<String>()
        if (hasColor) attrs.add("color:${colorToHex(s.color)}")
        if (hasFontSize) attrs.add("font-size:${s.fontSize.value.toInt()}px")
        t.add("<span style=\"${attrs.joinToString(";")}\">")
    }
    return t.joinToString("")
}

private fun styleToClose(s: SpanStyle): String {
    val t = mutableListOf<String>()
    if (s.color != Color.Unspecified || s.fontSize != TextUnit.Unspecified) t.add("</span>")
    if (s.textDecoration == TextDecoration.Underline) t.add("</u>")
    if (s.fontStyle == FontStyle.Italic) t.add("</em>")
    if (s.fontWeight == FontWeight.Bold) t.add("</strong>")
    return t.joinToString("")
}

private fun colorToHex(c: Color): String {
    val r = (c.red * 255).toInt().coerceIn(0, 255)
    val g = (c.green * 255).toInt().coerceIn(0, 255)
    val b = (c.blue * 255).toInt().coerceIn(0, 255)
    return "#${r.toString(16).padStart(2,'0')}${g.toString(16).padStart(2,'0')}${b.toString(16).padStart(2,'0')}".uppercase()
}

private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun hasStyle(spans: List<StyleSpan>, sel: TextRange, weight: FontWeight?, style: FontStyle?, decor: TextDecoration?): Boolean {
    val s = sel.min; val e = sel.max
    return spans.any { sp ->
        sp.start < e && sp.end > s && (
            (weight != null && sp.fontWeight == weight) ||
            (style != null && sp.fontStyle == style) ||
            (decor != null && sp.textDecoration == decor)
        )
    }
}

@Composable
fun RichTextCardEditor(
    front: String,
    onFrontChange: (String) -> Unit,
    back: String,
    onBackChange: (String) -> Unit,
    frontLabel: String,
    backLabel: String,
) {
    Text(frontLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    RichEditField(initialHtml = front, onHtmlChange = onFrontChange)
    Spacer(Modifier.height(8.dp))
    Text(backLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    RichEditField(initialHtml = back, onHtmlChange = onBackChange)
}

@Composable
private fun RichEditField(initialHtml: String, onHtmlChange: (String) -> Unit) {
    val strings = koinInject<I18nManager>().strings
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val initialState = remember(initialHtml) { parseHtmlToSpans(initialHtml) }
    var text by remember { mutableStateOf(initialState.first) }
    var spans by remember { mutableStateOf(initialState.second) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var savedSelection by remember { mutableStateOf(TextRange.Zero) }
    var showColorMenu by remember { mutableStateOf(false) }
    var showFontSizeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(text, spans) {
        delay(200)
        onHtmlChange(spansToHtml(text, spans))
    }

    val styledText by remember { derivedStateOf { buildAnnotated(text, spans) } }
    val visualTransformation = remember {
        VisualTransformation { TransformedText(styledText, OffsetMapping.Identity) }
    }

    if (!selection.collapsed) savedSelection = selection

    val isBold = hasStyle(spans, savedSelection, FontWeight.Bold, null, null)
    val isItalic = hasStyle(spans, savedSelection, null, FontStyle.Italic, null)
    val isUnderline = hasStyle(spans, savedSelection, null, null, TextDecoration.Underline)

    fun toggleProp(weight: FontWeight? = null, style: FontStyle? = null, decor: TextDecoration? = null) {
        val s = savedSelection; if (s.collapsed) return
        val mn = s.min; val mx = s.max
        var found = false
        val result = mutableListOf<StyleSpan>()
        for (sp in spans) {
            if (sp.start < mx && sp.end > mn && (
                (weight != null && sp.fontWeight == weight) ||
                (style != null && sp.fontStyle == style) ||
                (decor != null && sp.textDecoration == decor)
            )) {
                found = true
                val ovS = maxOf(sp.start, mn); val ovE = minOf(sp.end, mx)
                if (sp.start < ovS) result.add(sp.copy(end = ovS))
                val mid = when {
                    weight != null && sp.fontWeight == weight && (sp.fontStyle != null || sp.textDecoration != null || sp.color != null || sp.fontSize != null) -> sp.copy(fontWeight = null)
                    style != null && sp.fontStyle == style && (sp.fontWeight != null || sp.textDecoration != null || sp.color != null || sp.fontSize != null) -> sp.copy(fontStyle = null)
                    decor != null && sp.textDecoration == decor && (sp.fontWeight != null || sp.fontStyle != null || sp.color != null || sp.fontSize != null) -> sp.copy(textDecoration = null)
                    else -> null
                }
                if (mid != null) result.add(mid)
                if (sp.end > ovE) result.add(sp.copy(start = ovE))
            } else {
                result.add(sp)
            }
        }
        spans = if (found) {
            mergeSpans(result)
        } else {
            mergeSpans(spans + StyleSpan(mn, mx, fontWeight = weight, fontStyle = style, textDecoration = decor))
        }
    }

    fun applyColor(color: Color?) {
        val s = savedSelection; if (s.collapsed) return
        val mn = s.min; val mx = s.max
        if (color != null) {
            spans = mergeSpans(spans + StyleSpan(mn, mx, color = color))
        } else {
            val result = mutableListOf<StyleSpan>()
            for (sp in spans) {
                if (sp.start < mx && sp.end > mn && sp.color != null) {
                    val ovS = maxOf(sp.start, mn); val ovE = minOf(sp.end, mx)
                    if (sp.start < ovS) result.add(sp.copy(end = ovS))
                    if (sp.fontWeight != null || sp.fontStyle != null || sp.textDecoration != null || sp.fontSize != null)
                        result.add(sp.copy(color = null))
                    if (sp.end > ovE) result.add(sp.copy(start = ovE))
                } else {
                    result.add(sp)
                }
            }
            spans = mergeSpans(result)
        }
    }

    fun applyFontSize(sizeSp: Int) {
        val s = savedSelection; if (s.collapsed) return
        val mn = s.min; val mx = s.max
        spans = mergeSpans(spans + StyleSpan(mn, mx, fontSize = sizeSp.sp))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(onClick = { toggleProp(weight = FontWeight.Bold) }, selected = isBold, icon = { Text("B", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) })
            ToolbarButton(onClick = { toggleProp(style = FontStyle.Italic) }, selected = isItalic, icon = { Text("I", fontStyle = FontStyle.Italic, fontSize = 14.sp) })
            ToolbarButton(onClick = { toggleProp(decor = TextDecoration.Underline) }, selected = isUnderline, icon = { Text("U", fontSize = 14.sp) })

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
                            onClick = { applyColor(color); showColorMenu = false },
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
                            onClick = { applyFontSize(size); showFontSizeMenu = false },
                        )
                    }
                }
            }

        }

        BasicTextField(
            value = TextFieldValue(text = text, selection = selection),
            onValueChange = { newValue ->
                val oldText = text
                val newText = newValue.text
                selection = newValue.selection
                if (oldText != newText) {
                    val newSpans = rebuildSpans(oldText, newText, spans)
                    text = newText
                    spans = newSpans
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).focusRequester(focusRequester),
            visualTransformation = visualTransformation,
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun ToolbarButton(onClick: () -> Unit, selected: Boolean, icon: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.size(32.dp),
    ) { Box(contentAlignment = Alignment.Center) { icon() } }
}
