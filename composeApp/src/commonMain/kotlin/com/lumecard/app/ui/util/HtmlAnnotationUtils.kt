package com.lumecard.app.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal fun htmlToAnnotatedString(html: String): AnnotatedString {
    val input = html.trim()
    if (!input.contains("<") || !input.contains(">"))
        return buildAnnotatedString { append(input) }

    return buildAnnotatedString {
        var i = 0
        val tagStack = mutableListOf<SpanStyle>()
        var endsWithNewline = false

        fun currentStyle(): SpanStyle {
            var style = SpanStyle()
            for (s in tagStack.reversed()) {
                val merged = SpanStyle(
                    fontWeight = s.fontWeight ?: style.fontWeight,
                    fontStyle = s.fontStyle ?: style.fontStyle,
                    textDecoration = s.textDecoration ?: style.textDecoration,
                    color = if (s.color != Color.Unspecified) s.color else style.color,
                    fontSize = if (s.fontSize != TextUnit.Unspecified) s.fontSize else style.fontSize,
                )
                style = merged
            }
            return style
        }

        while (i < input.length) {
            if (input[i] == '<') {
                val close = input.indexOf('>', i)
                if (close == -1) { append(input.substring(i)); break }
                val tag = input.substring(i + 1, close)
                i = close + 1

                when {
                    tag.startsWith("/") -> {
                        val name = tag.substring(1).split(" ", ">").first().lowercase()
                        when (name) {
                            "b", "strong", "i", "em", "u", "span", "p", "div" ->
                                if (tagStack.isNotEmpty()) tagStack.removeAt(tagStack.lastIndex)
                        }
                        if (name == "p" || name == "div") endsWithNewline = false
                    }
                    tag.startsWith("br") -> { append("\n"); endsWithNewline = true }
                    tag.startsWith("p") -> {
                        if (!endsWithNewline && length > 0) { append("\n"); endsWithNewline = true }
                        tagStack.add(SpanStyle())
                    }
                    tag.startsWith("div") -> {
                        if (!endsWithNewline && length > 0) { append("\n"); endsWithNewline = true }
                        tagStack.add(SpanStyle())
                    }
                    tag.startsWith("b") || tag.startsWith("strong") ->
                        tagStack.add(SpanStyle(fontWeight = FontWeight.Bold))
                    tag.startsWith("i") || tag.startsWith("em") ->
                        tagStack.add(SpanStyle(fontStyle = FontStyle.Italic))
                    tag.startsWith("u") ->
                        tagStack.add(SpanStyle(textDecoration = TextDecoration.Underline))
                    tag.startsWith("span") -> {
                        val styleAttr = parseStyleAttribute(tag)
                        tagStack.add(styleAttr)
                    }
                }
            } else {
                val nextTag = input.indexOf('<', i)
                val text = if (nextTag == -1) input.substring(i) else input.substring(i, nextTag)
                i = if (nextTag == -1) input.length else nextTag

                val cleaned = text.replace("\n", " ").replace("\r", "")
                if (cleaned.isNotEmpty()) {
                    withStyle(currentStyle()) { append(cleaned) }
                    endsWithNewline = cleaned.endsWith('\n')
                }
            }
        }
    }
}

private fun parseStyleAttribute(tag: String): SpanStyle {
    val styleMatch = Regex("""style\s*=\s*"([^"]*)"""").find(tag)
    val styles = styleMatch?.groupValues?.getOrNull(1) ?: return SpanStyle()
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var textDecoration: TextDecoration? = null
    var color: Color? = null
    var fontSize: TextUnit? = null

    for (decl in styles.split(";")) {
        val parts = decl.trim().split(":", limit = 2)
        if (parts.size != 2) continue
        val prop = parts[0].trim().lowercase()
        val value = parts[1].trim().lowercase()
        when (prop) {
            "font-weight" -> {
                when (value) {
                    "bold", "700", "800", "900" -> fontWeight = FontWeight.Bold
                    "600" -> fontWeight = FontWeight.SemiBold
                    "500" -> fontWeight = FontWeight.Medium
                    "400", "normal" -> fontWeight = FontWeight.Normal
                    "300" -> fontWeight = FontWeight.Light
                }
            }
            "font-style" -> {
                if (value == "italic") fontStyle = FontStyle.Italic
            }
            "text-decoration" -> {
                if (value.contains("underline")) textDecoration = TextDecoration.Underline
                if (value.contains("line-through")) textDecoration = TextDecoration.LineThrough
            }
            "color" -> {
                color = parseHtmlColor(value)
            }
            "font-size" -> {
                val numMatch = Regex("""(\d+(?:\.\d+)?)""").find(value)
                if (numMatch != null) {
                    val num = numMatch.groupValues[1].toFloatOrNull()
                    if (num != null) fontSize = num.sp
                }
            }
        }
    }
    return SpanStyle(
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
        color = color ?: Color.Unspecified,
        fontSize = fontSize ?: TextUnit.Unspecified,
    )
}


