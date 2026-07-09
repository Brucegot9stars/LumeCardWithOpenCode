package com.lumecard.app.ui.screens.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.SettingsSearchBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.help.HelpArticle
import com.lumecard.shared.help.HelpSearchResult
import org.koin.compose.koinInject

@Composable
fun HelpTocPanel(
    articles: List<HelpArticle>,
    selectedArticleId: String?,
    searchQuery: String,
    searchResults: List<HelpSearchResult>,
    onSearchQueryChange: (String) -> Unit,
    onArticleClick: (HelpArticle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = koinInject<I18nManager>().strings
    val spacing = LumeCardTheme.spacing

    Column(modifier = modifier) {
        SettingsSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = strings.helpCenterSearchPlaceholder,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.sm),
        )

        if (searchQuery.isNotBlank()) {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        strings.helpCenterNoResults,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchResults, key = { it.article.id }) { result ->
                        SearchResultItem(
                            result = result,
                            isSelected = result.article.id == selectedArticleId,
                            onClick = { onArticleClick(result.article) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.sm)) {
                item {
                    Text(
                        strings.helpCenterTableOfContents,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = spacing.sm, horizontal = spacing.xs),
                    )
                }
                items(articles, key = { it.id }) { article ->
                    TocArticleItem(
                        article = article,
                        isSelected = article.id == selectedArticleId,
                        onClick = { onArticleClick(article) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TocArticleItem(
    article: HelpArticle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (article.hasDangerContent) {
                Icon(
                    Icons.Default.Dangerous,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                article.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (article.hasDangerContent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: HelpSearchResult,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                result.article.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (result.matchedOn.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Matched: ${result.matchedOn.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
