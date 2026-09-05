package com.lumecard.app.ui.screens.help

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.screens.dashboard.DashboardScreen
import com.lumecard.shared.help.HelpArticle
import com.lumecard.shared.help.HelpRepository
import com.lumecard.shared.help.HelpSearchResult
import org.koin.compose.koinInject

class HelpScreen(
    private val initialArticleId: String? = null,
) : Screen {
    override val key: ScreenKey = "HelpCenter"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val strings = koinInject<I18nManager>().strings
        val helpRepository: HelpRepository = koinInject()

        val currentLocale = koinInject<I18nManager>().resolvedLocaleCode

        var articles by remember { mutableStateOf(helpRepository.getArticles(currentLocale)) }
        var selectedArticle by remember { mutableStateOf<HelpArticle?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<HelpSearchResult>>(emptyList()) }

        LaunchedEffect(initialArticleId) {
            if (initialArticleId != null) {
                selectedArticle = helpRepository.getArticle(currentLocale, initialArticleId)
            }
        }

        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                searchResults = emptyList()
            } else {
                searchResults = helpRepository.search(currentLocale, searchQuery)
            }
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.helpCenter,
                    onBack = { navigator.replace(DashboardScreen()) },
                )
            }
        ) { padding ->
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                HelpTocPanel(
                    articles = articles,
                    selectedArticleId = selectedArticle?.id,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onSearchQueryChange = { searchQuery = it },
                    onArticleClick = { article ->
                        selectedArticle = article
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp),
                )

                if (selectedArticle != null) {
                    VerticalDivider()
                    HelpArticleView(
                        article = selectedArticle!!,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
