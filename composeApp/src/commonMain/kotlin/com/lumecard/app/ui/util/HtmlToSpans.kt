package com.lumecard.app.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

data class StyleSpan(
    val start: Int,
    var end: Int,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textDecoration: TextDecoration? = null,
    val color: Color? = null,
    val fontSize: TextUnit? = null,
) {
    fun toSpanStyle(): SpanStyle = SpanStyle(
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
        color = color ?: Color.Unspecified,
        fontSize = fontSize ?: TextUnit.Unspecified,
    )
}

private data class TagStyle(
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textDecoration: TextDecoration? = null,
    val color: Color = Color.Unspecified,
    val fontSize: TextUnit = TextUnit.Unspecified,
)

fun parseHtmlToSpans(html: String): Pair<String, List<StyleSpan>> {
    val text = StringBuilder()
    val spans = mutableListOf<StyleSpan>()
    val tagStack = mutableListOf<TagStyle>()

    fun currentStyle(): TagStyle {
        var w: FontWeight? = null
        var s: FontStyle? = null
        var d: TextDecoration? = null
        var c = Color.Unspecified
        var fs = TextUnit.Unspecified
        for (ts in tagStack) {
            if (ts.fontWeight != null) w = ts.fontWeight
            if (ts.fontStyle != null) s = ts.fontStyle
            if (ts.textDecoration != null) d = ts.textDecoration
            if (ts.color != Color.Unspecified) c = ts.color
            if (ts.fontSize != TextUnit.Unspecified) fs = ts.fontSize
        }
        return TagStyle(w, s, d, c, fs)
    }

    val tagRegex = Regex("<(/?)($htmlTagPattern)((?:\\s[^>]*)?)\\s*/?>", RegexOption.IGNORE_CASE)
    var lastEnd = 0

    for (match in tagRegex.findAll(html)) {
        if (match.range.first > lastEnd) {
            val raw = html.substring(lastEnd, match.range.first)
            text.append(raw.replace("\r\n", "\n").replace('\r', ' '))
        }
        val isClose = match.groupValues[1] == "/"
        val tagName = match.groupValues[2].lowercase()
        val attrs = match.groupValues[3]

        if (isClose) {
            if (tagStack.isNotEmpty() && tagStack.last().let { matchesTag(it, tagName) }) {
                val start = text.length
                val ts = tagStack.removeAt(tagStack.lastIndex)
                val cs = currentStyle()
                spans.add(
                    StyleSpan(
                        start = start,
                        end = text.length,
                        fontWeight = ts.fontWeight ?: cs.fontWeight,
                        fontStyle = ts.fontStyle ?: cs.fontStyle,
                        textDecoration = ts.textDecoration ?: cs.textDecoration,
                        color = if (ts.color != Color.Unspecified) ts.color else cs.color,
                        fontSize = if (ts.fontSize != TextUnit.Unspecified) ts.fontSize else cs.fontSize,
                    )
                )
            }
        } else {
            when (tagName) {
                "br" -> text.append('\n')
                "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li" -> {
                    if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
                }
                "hr" -> {
                    if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
                    text.append("---\n")
                }
            }

            val tagStyle = parseTagStyle(tagName, attrs)
            if (tagStyle != null) {
                val s = text.length
                tagStack.add(tagStyle)
            }
        }
        lastEnd = match.range.last + 1
    }

    if (lastEnd < html.length) {
        text.append(html.substring(lastEnd).replace("\r\n", "\n").replace('\r', ' '))
    }

    // pop remaining open tags
    var pos = text.length
    for (i in tagStack.indices.reversed()) {
        val ts = tagStack[i]
        val after = if (i > 0) {
            var w: FontWeight? = null; var s: FontStyle? = null; var d: TextDecoration? = null
            var c = Color.Unspecified; var fs = TextUnit.Unspecified
            for (j in 0 until i) {
                val t = tagStack[j]
                if (t.fontWeight != null) w = t.fontWeight
                if (t.fontStyle != null) s = t.fontStyle
                if (t.textDecoration != null) d = t.textDecoration
                if (t.color != Color.Unspecified) c = t.color
                if (t.fontSize != TextUnit.Unspecified) fs = t.fontSize
            }
            TagStyle(w, s, d, c, fs)
        } else TagStyle()
        spans.add(
            StyleSpan(
                start = pos, end = pos,
                fontWeight = ts.fontWeight ?: after.fontWeight,
                fontStyle = ts.fontStyle ?: after.fontStyle,
                textDecoration = ts.textDecoration ?: after.textDecoration,
                color = if (ts.color != Color.Unspecified) ts.color else after.color,
                fontSize = if (ts.fontSize != TextUnit.Unspecified) ts.fontSize else after.fontSize,
            )
        )
    }

    return Pair(text.toString(), spans)
}

fun parseHtmlToAnnotatedString(html: String) = buildAnnotatedString {
    val (text, spans) = parseHtmlToSpans(html)
    if (spans.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    val sorted = spans.sortedBy { it.start }
    var pos = 0
    for (sp in sorted) {
        if (sp.start == sp.end) continue
        if (pos < sp.start) append(text.substring(pos, sp.start))
        withStyle(sp.toSpanStyle()) { append(text.substring(sp.start, sp.end)) }
        pos = sp.end
    }
    if (pos < text.length) append(text.substring(pos))
}

private const val htmlTagPattern = "(?:b|strong|i|em|u|span|p|div|br|h[1-6]|ul|ol|li|a|code|pre|blockquote|hr|img|sub|sup)"

private fun matchesTag(ts: TagStyle, tagName: String): Boolean = when (tagName) {
    "b", "strong" -> ts.fontWeight == FontWeight.Bold
    "i", "em" -> ts.fontStyle == FontStyle.Italic
    "u" -> ts.textDecoration == TextDecoration.Underline
    "span" -> true
    else -> false
}

private fun parseTagStyle(tagName: String, attrs: String): TagStyle? {
    return when (tagName) {
        "b", "strong" -> TagStyle(fontWeight = FontWeight.Bold)
        "i", "em" -> TagStyle(fontStyle = FontStyle.Italic)
        "u" -> TagStyle(textDecoration = TextDecoration.Underline)
        "sub" -> TagStyle(fontSize = TextUnit.Unspecified) // placeholder
        "sup" -> TagStyle(fontSize = TextUnit.Unspecified)
        "span" -> parseSpanAttrs(attrs)
        else -> null
    }
}

private fun parseSpanAttrs(attrs: String): TagStyle? {
    val styleRegex = Regex("""style\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    val styleMatch = styleRegex.find(attrs) ?: return TagStyle()
    val css = styleMatch.groupValues[1]
    var color = Color.Unspecified
    var fontSize = TextUnit.Unspecified
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var textDec: TextDecoration? = null

    for (decl in css.split(";")) {
        val parts = decl.trim().split(":", limit = 2)
        if (parts.size != 2) continue
        val key = parts[0].trim().lowercase()
        val value = parts[1].trim()
        when (key) {
            "color" -> color = parseHtmlColor(value) ?: Color.Unspecified
            "font-size" -> {
                val num = value.replace("px", "").trim().toFloatOrNull()
                if (num != null) fontSize = TextUnit.Unspecified // stored elsewhere
            }
            "font-weight" -> if (value.toIntOrNull() != null && value.toInt() >= 700) fontWeight = FontWeight.Bold
            "font-style" -> if (value == "italic") fontStyle = FontStyle.Italic
            "text-decoration" -> if (value.contains("underline")) textDec = TextDecoration.Underline
        }
    }
    if (color == Color.Unspecified && fontWeight == null && fontStyle == null && textDec == null) return null
    return TagStyle(fontWeight, fontStyle, textDec, color, fontSize)
}
