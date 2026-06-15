package com.lumecard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    val lines = markdown.split("\n")
    val codeBlockLines = mutableListOf<String>()
    var inCodeBlock = false
    var codeLanguage: String? = null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("```") -> {
                    if (inCodeBlock) {
                        CodeBlock(codeBlockLines.joinToString("\n"), codeLanguage)
                        codeBlockLines.clear()
                        codeLanguage = null
                        inCodeBlock = false
                    } else {
                        codeLanguage = line.removePrefix("```").trim().ifBlank { null }
                        inCodeBlock = true
                    }
                    i++
                }
                inCodeBlock -> {
                    codeBlockLines.add(line)
                    i++
                }
                line.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                    var tableEnd = i + 2
                    while (tableEnd < lines.size && lines[tableEnd].startsWith("|")) tableEnd++
                    TableBlock(lines.subList(i, tableEnd))
                    i = tableEnd
                }
                line.startsWith("# ") -> {
                    Text(
                        line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    i++
                }
                line.startsWith("## ") -> {
                    Text(
                        line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    i++
                }
                line.startsWith("### ") -> {
                    Text(
                        line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    i++
                }
                line.startsWith("> ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        InlineMarkdownText(
                            text = line.removePrefix("> "),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    i++
                }
                line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    i++
                }
                line.matches(Regex("^- \\[[xX ]].*")) -> {
                    val checked = line.getOrNull(3) == 'x' || line.getOrNull(3) == 'X'
                    val taskText = line.substring(5)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (checked) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(3.dp))
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        InlineMarkdownText(
                            text = taskText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    i++
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge)
                        InlineMarkdownText(
                            text = line.removePrefix("- ").removePrefix("* "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    i++
                }
                line.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val num = line.substringBefore(".")
                    val content = line.substringAfter(". ")
                    Row {
                        Text("$num.  ", style = MaterialTheme.typography.bodyLarge)
                        InlineMarkdownText(
                            text = content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    i++
                }
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    i++
                }
                else -> {
                    InlineMarkdownText(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    i++
                }
            }
        }
        if (codeBlockLines.isNotEmpty()) {
            CodeBlock(codeBlockLines.joinToString("\n"), codeLanguage)
        }
    }
}

@Composable
private fun TableBlock(rows: List<String>) {
    val parsed = rows.map { row ->
        row.trim('|').split("|").map { it.trim() }
    }
    if (parsed.size < 2) return
    val header = parsed[0]
    val dataRows = parsed.drop(2)
    val colCount = header.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                header.forEachIndexed { _, cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            dataRows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    (0 until colCount).forEach { col ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            InlineMarkdownText(
                                text = row.getOrElse(col) { "" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (row != dataRows.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    style: TextStyle,
    color: Color
) {
    Text(
        text = parseInlineMarkdown(text, color),
        style = style
    )
}

private fun parseInlineMarkdown(text: String, baseColor: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.1f))) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("[", i) -> {
                    val closeBracket = text.indexOf("](", i)
                    if (closeBracket != -1) {
                        val parenClose = text.indexOf(")", closeBracket)
                        if (parenClose != -1) {
                            val linkText = text.substring(i + 1, closeBracket)
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = baseColor)) {
                                append(linkText)
                            }
                            i = parenClose + 1
                        } else {
                            append(text[i]); i++
                        }
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("![", i) -> {
                    val closeBracket = text.indexOf("](", i)
                    if (closeBracket != -1) {
                        val parenClose = text.indexOf(")", closeBracket)
                        if (parenClose != -1) {
                            val altText = text.substring(i + 2, closeBracket)
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor.copy(alpha = 0.7f))) {
                                append("[$altText]")
                            }
                            i = parenClose + 1
                        } else {
                            append(text[i]); i++
                        }
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("$$", i) -> {
                    val end = text.indexOf("$$", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.1f))) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("$", i) && !text.startsWith("$$", i) -> {
                    val end = text.indexOf("$", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.1f))) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                else -> {
                    append(text[i]); i++
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column {
            if (language != null) {
                Text(
                    text = language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp)
                )
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

private fun isTableSeparator(line: String): Boolean {
    return line.startsWith("|") && line.all { c -> c == '|' || c == '-' || c == ':' || c == ' ' || c == '\t' }
}
