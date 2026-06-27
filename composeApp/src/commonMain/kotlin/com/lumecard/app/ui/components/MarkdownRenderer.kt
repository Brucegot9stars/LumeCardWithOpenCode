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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.i18n.I18nManager
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import org.koin.compose.koinInject

private fun centeredMarkdownTypography(default: MarkdownTypography): MarkdownTypography {
    return object : MarkdownTypography {
        override val text: TextStyle get() = default.text.copy(textAlign = TextAlign.Center)
        override val code: TextStyle get() = default.code
        override val inlineCode: TextStyle get() = default.inlineCode
        override val h1: TextStyle get() = default.h1.copy(textAlign = TextAlign.Center)
        override val h2: TextStyle get() = default.h2.copy(textAlign = TextAlign.Center)
        override val h3: TextStyle get() = default.h3.copy(textAlign = TextAlign.Center)
        override val h4: TextStyle get() = default.h4
        override val h5: TextStyle get() = default.h5
        override val h6: TextStyle get() = default.h6
        override val quote: TextStyle get() = default.quote
        override val paragraph: TextStyle get() = default.paragraph.copy(textAlign = TextAlign.Center)
        override val ordered: TextStyle get() = default.ordered.copy(textAlign = TextAlign.Center)
        override val bullet: TextStyle get() = default.bullet.copy(textAlign = TextAlign.Center)
        override val list: TextStyle get() = default.list
        override val textLink: androidx.compose.ui.text.TextLinkStyles get() = default.textLink
        override val table: TextStyle get() = default.table
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    center: Boolean = false,
) {
    val segments = remember(markdown) { splitMathBlocks(markdown) }
    val default = markdownTypography()
    val typography = remember(center, default) {
        if (center) centeredMarkdownTypography(default) else default
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Math -> MathBlock(segment.content)
                is Segment.Markdown -> {
                    Markdown(
                        content = segment.text,
                        modifier = Modifier.fillMaxWidth(),
                        typography = typography,
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


