package com.lumecard.app.ui.screens.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.help.HelpArticle
import com.lumecard.shared.help.HelpSection
import org.koin.compose.koinInject

@Composable
fun HelpArticleView(
    article: HelpArticle,
    modifier: Modifier = Modifier,
) {
    val strings = koinInject<I18nManager>().strings
    val spacing = LumeCardTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.md),
    ) {
        if (article.hasDangerContent) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Dangerous,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.helpCenterDangerContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Text(
            article.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (article.tags.isNotEmpty()) {
            Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                article.tags.forEach { tag ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        Spacer(Modifier.height(spacing.md))

        article.sections.forEachIndexed { index, section ->
            HelpSectionView(section = section)
            if (index < article.sections.lastIndex) {
                Spacer(Modifier.height(spacing.lg))
            }
        }

        if (article.relatedArticles.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xl))
            HorizontalDivider()
            Spacer(Modifier.height(spacing.md))
            Text(
                strings.helpCenterRelatedArticles,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.sm))
            article.relatedArticles.forEach { related ->
                Text(
                    related,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(spacing.xxl))
    }
}
