package com.lumecard.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.i18n.I18nManager
import com.mikepenz.markdown.m3.Markdown
import org.koin.compose.koinInject

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val segments = remember(markdown) { splitMathBlocks(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Math -> MathBlock(segment.content)
                is Segment.Markdown -> {
                    Markdown(
                        content = segment.text,
                        modifier = Modifier.fillMaxWidth(),
                    )
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

@Composable
fun MathBlock(mathContent: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = mathContent,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
fun MermaidBlock(diagram: String) {
    val strings = koinInject<I18nManager>().strings
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = strings.mermaidChartTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = diagram,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
