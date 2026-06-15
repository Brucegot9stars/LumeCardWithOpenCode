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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.*
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser

private val LinkColor = Color(0xFF0969DA)

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    val parser = remember { createParser() }
    val segments = remember(markdown) { splitMathBlocks(markdown) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Math -> MathBlock(segment.content)
                is Segment.Markdown -> {
                    val doc = parser.parse(segment.text)
                    var child = doc.firstChild
                    while (child != null) {
                        RenderNode(child)
                        child = child.next
                    }
                }
            }
        }
    }
}

private sealed class Segment {
    data class Math(val content: String) : Segment()
    data class Markdown(val text: String) : Segment()
}

private fun splitMathBlocks(markdown: String): List<Segment> {
    val segments = mutableListOf<Segment>()
    val regex = Regex("""(?s)\$\$(.+?)\$\$""")
    var lastEnd = 0
    for (match in regex.findAll(markdown)) {
        if (match.range.first > lastEnd) {
            segments.add(Segment.Markdown(markdown.substring(lastEnd, match.range.first)))
        }
        segments.add(Segment.Math(match.groupValues[1].trim()))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < markdown.length) {
        segments.add(Segment.Markdown(markdown.substring(lastEnd)))
    }
    return segments
}

private fun createParser(): Parser {
    return Parser.builder()
        .extensions(listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create(),
            TaskListItemsExtension.create()
        ))
        .build()
}

@Composable
private fun RenderNode(node: Node) {
    when (node) {
        is Heading -> RenderHeading(node)
        is Paragraph -> RenderParagraph(node)
        is FencedCodeBlock -> renderCodeBlock(node)
        is ThematicBreak -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        is BulletList -> RenderList(node, ordered = false, tight = node.isTight)
        is OrderedList -> RenderList(node, ordered = true, tight = node.isTight, startNumber = node.startNumber)
        is BlockQuote -> RenderBlockQuote(node)
        is TableBlock -> RenderTable(node)
        is HtmlBlock -> { /* skip HTML blocks */ }
        else -> {}
    }
}

@Composable
private fun RenderHeading(node: Heading) {
    val style = when (node.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val topPad = when (node.level) {
        1 -> 8.dp; 2 -> 6.dp; 3 -> 4.dp; else -> 2.dp
    }
    val bottomPad = when (node.level) {
        1 -> 4.dp; 2 -> 4.dp; 3 -> 2.dp; else -> 2.dp
    }
    Text(
        text = collectInlineContent(node, MaterialTheme.colorScheme.onSurface),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = topPad, bottom = bottomPad)
    )
}

@Composable
private fun RenderParagraph(node: Paragraph) {
    Text(
        text = collectInlineContent(node, MaterialTheme.colorScheme.onSurface),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RenderList(
    node: Node,
    ordered: Boolean,
    tight: Boolean,
    startNumber: Int = 1
) {
    val spacing = if (tight) 0.dp else 4.dp
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        var index = startNumber
        var child = node.firstChild
        while (child != null) {
            if (child is ListItem) {
                RenderListItem(child, ordered, index)
                index++
            }
            child = child.next
        }
    }
}

@Composable
private fun RenderListItem(node: ListItem, ordered: Boolean, index: Int) {
    val hasTaskMarker = node.firstChild is TaskListItemMarker ||
            node.firstChild?.let { it is Paragraph && it.firstChild is TaskListItemMarker } == true

    if (hasTaskMarker) {
        RenderTaskListItem(node)
    } else {
        Row(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = if (ordered) "$index. " else "•  ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (ordered) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(modifier = Modifier.weight(1f)) {
                var child = node.firstChild
                while (child != null) {
                    if (child !is TaskListItemMarker) {
                        RenderNode(child)
                    }
                    child = child.next
                }
            }
        }
    }
}

@Composable
private fun RenderTaskListItem(node: ListItem) {
    var marker: TaskListItemMarker? = null
    var child = node.firstChild
    while (child != null) {
        if (child is TaskListItemMarker) {
            marker = child
            break
        }
        if (child is Paragraph && child.firstChild is TaskListItemMarker) {
            marker = child.firstChild as TaskListItemMarker
        }
        child = child.next
    }
    val checked = marker?.isChecked == true

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            child = node.firstChild
            while (child != null) {
                if (child !is TaskListItemMarker) {
                    if (child is Paragraph) {
                        val withoutMarker = Paragraph()
                        var gc = child.firstChild
                        while (gc != null) {
                            if (gc !is TaskListItemMarker) {
                                withoutMarker.appendChild(gc)
                            }
                            gc = gc.next
                        }
                        RenderParagraph(withoutMarker)
                    } else {
                        RenderNode(child)
                    }
                }
                child = child.next
            }
        }
    }
}

@Composable
private fun RenderBlockQuote(node: BlockQuote) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var child = node.firstChild
                while (child != null) {
                    RenderNode(child)
                    child = child.next
                }
            }
        }
    }
}

@Composable
private fun renderCodeBlock(node: FencedCodeBlock) {
    if (node.info == "mermaid") {
        MermaidBlock(node.literal)
    } else {
        CodeBlock(
            code = node.literal,
            language = node.info.ifBlank { null }
        )
    }
}

@Composable
private fun RenderTable(node: TableBlock) {
    val head = node.firstChild as? TableHead
    val body = node.firstChild?.next as? TableBody
    if (head == null || body == null) return

    val headerRows = mutableListOf<TableRow>()
    var child = head.firstChild
    while (child != null) {
        if (child is TableRow) headerRows.add(child)
        child = child.next
    }
    val dataRows = mutableListOf<TableRow>()
    child = body.firstChild
    while (child != null) {
        if (child is TableRow) dataRows.add(child)
        child = child.next
    }
    if (headerRows.isEmpty()) return
    val headerRow = headerRows.first()

    val headerCells = mutableListOf<TableCell>()
    var cell = headerRow.firstChild
    while (cell != null) {
        if (cell is TableCell) headerCells.add(cell)
        cell = cell.next
    }
    val colCount = headerCells.size
    if (colCount == 0) return

    val alignments = headerCells.map { cell ->
        when (cell.alignment) {
            TableCell.Alignment.CENTER -> Alignment.TopCenter
            TableCell.Alignment.RIGHT -> Alignment.TopEnd
            else -> Alignment.TopStart
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                    )
            ) {
                headerCells.forEachIndexed { idx, hc ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = alignments.getOrElse(idx) { Alignment.TopStart }
                    ) {
                        Text(
                            text = collectInlineContent(hc, MaterialTheme.colorScheme.onSurface),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            dataRows.forEachIndexed { rowIdx, row ->
                val rowCells = mutableListOf<TableCell>()
                var rc = row.firstChild
                while (rc != null) {
                    if (rc is TableCell) rowCells.add(rc)
                    rc = rc.next
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (rowIdx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                ) {
                    (0 until colCount).forEach { col ->
                        val cell = rowCells.getOrNull(col)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = alignments.getOrElse(col) { Alignment.TopStart }
                        ) {
                            val cellText = if (cell != null) collectInlineContent(cell, MaterialTheme.colorScheme.onSurface)
                                else AnnotatedString("")
                            Text(
                                text = cellText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (rowIdx < dataRows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column {
            if (language != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = language.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun MathBlock(mathContent: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = mathContent,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun MermaidBlock(diagram: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📊",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Mermaid 流程图",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = diagram,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

private fun collectInlineContent(node: Node, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var child = node.firstChild
        while (child != null) {
            appendInlineNode(child, baseColor)
            child = child.next
        }
    }
}

private fun AnnotatedString.Builder.appendInlineNode(node: Node, baseColor: Color) {
    when (node) {
        is Text -> append(node.literal)
        is Code -> {
            withStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = baseColor.copy(alpha = 0.1f),
                fontSize = 14.sp
            )) {
                append(node.literal)
            }
        }
        is Emphasis -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                var child = node.firstChild
                while (child != null) {
                    appendInlineNode(child, baseColor)
                    child = child.next
                }
            }
        }
        is StrongEmphasis -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                var child = node.firstChild
                while (child != null) {
                    appendInlineNode(child, baseColor)
                    child = child.next
                }
            }
        }
        is Strikethrough -> {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                var child = node.firstChild
                while (child != null) {
                    appendInlineNode(child, baseColor)
                    child = child.next
                }
            }
        }
        is Link -> {
            withStyle(SpanStyle(
                color = LinkColor,
                textDecoration = TextDecoration.Underline
            )) {
                var child = node.firstChild
                while (child != null) {
                    appendInlineNode(child, baseColor)
                    child = child.next
                }
            }
        }
        is Image -> {
            append("📷 ")
            var child = node.firstChild
            while (child != null) {
                appendInlineNode(child, baseColor)
                child = child.next
            }
        }
        is HtmlInline -> append(node.literal)
        is SoftLineBreak -> append(" ")
        is HardLineBreak -> append("\n")
        else -> {
            var child = node.firstChild
            while (child != null) {
                appendInlineNode(child, baseColor)
                child = child.next
            }
        }
    }
}
