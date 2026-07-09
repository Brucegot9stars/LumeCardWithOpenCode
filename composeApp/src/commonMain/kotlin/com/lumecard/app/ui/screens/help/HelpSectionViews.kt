package com.lumecard.app.ui.screens.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.help.*
import org.koin.compose.koinInject

private val LinkRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")

@Composable
fun HelpSectionView(
    section: HelpSection,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is ParagraphSection -> ParagraphSectionView(section, modifier)
        is StepsSection -> StepsSectionView(section, modifier)
        is TipSection -> TipSectionView(section, modifier)
        is WarningSection -> WarningSectionView(section, modifier)
        is DangerSection -> DangerSectionView(section, modifier)
        is OrderedListSection -> OrderedListSectionView(section, modifier)
        is BulletListSection -> BulletListSectionView(section, modifier)
        is FAQSection -> FAQSectionView(section, modifier)
    }
}

@Composable
private fun ParagraphSectionView(
    section: ParagraphSection,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = section.title
    Column(modifier = modifier) {
        if (sectionTitle != null) {
            Text(
                sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
        }
        RichText(section.content)
    }
}

@Composable
private fun StepsSectionView(
    section: StepsSection,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = section.title
    Column(modifier = modifier) {
        if (sectionTitle != null) {
            Text(
                sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
        }
        section.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
                RichText(item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TipSectionView(
    section: TipSection,
    modifier: Modifier = Modifier,
) {
    val strings = koinInject<I18nManager>().strings
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    strings.helpCenterTip,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (section.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RichText(section.content)
            }
        }
    }
}

@Composable
private fun WarningSectionView(
    section: WarningSection,
    modifier: Modifier = Modifier,
) {
    val strings = koinInject<I18nManager>().strings
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    strings.helpCenterWarning,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (section.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RichText(section.content)
            }
        }
    }
}

@Composable
private fun DangerSectionView(
    section: DangerSection,
    modifier: Modifier = Modifier,
) {
    val strings = koinInject<I18nManager>().strings
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Dangerous,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    strings.helpCenterDanger,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (section.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RichText(
                    section.content,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun OrderedListSectionView(
    section: OrderedListSection,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = section.title
    Column(modifier = modifier) {
        if (sectionTitle != null) {
            Text(
                sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
        }
        section.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
                RichText(item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BulletListSectionView(
    section: BulletListSection,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = section.title
    Column(modifier = modifier) {
        if (sectionTitle != null) {
            Text(
                sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
        }
        section.items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
                RichText(item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FAQSectionView(
    section: FAQSection,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = section.title
    Column(modifier = modifier) {
        if (sectionTitle != null) {
            Text(
                sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
        }
        section.items.forEach { item ->
            var expanded by remember { mutableStateOf(false) }
            Surface(
                onClick = { expanded = !expanded },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.question,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            RichText(item.answer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val parts = remember(text) { parseLinks(text) }

    Column(modifier = modifier) {
        parts.forEach { part ->
            when (part) {
                is RichPart.Text -> {
                    Text(
                        part.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
                is RichPart.Link -> {
                    ClickableText(
                        text = buildAnnotatedString {
                            pushStringAnnotation("url", part.url)
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                )
                            ) {
                                append(part.label)
                            }
                            pop()
                        },
                        onClick = { /* link handling TBD */ },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private sealed interface RichPart {
    data class Text(val content: String) : RichPart
    data class Link(val label: String, val url: String) : RichPart
}

private fun parseLinks(text: String): List<RichPart> {
    val result = mutableListOf<RichPart>()
    var lastIndex = 0
    LinkRegex.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            result.add(RichPart.Text(text.substring(lastIndex, match.range.first)))
        }
        result.add(RichPart.Link(match.groupValues[1], match.groupValues[2]))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        result.add(RichPart.Text(text.substring(lastIndex)))
    }
    return result
}
