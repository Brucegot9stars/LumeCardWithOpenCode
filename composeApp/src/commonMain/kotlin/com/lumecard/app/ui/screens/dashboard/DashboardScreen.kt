package com.lumecard.app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.screens.deck.CardListScreen
import com.lumecard.app.ui.screens.deck.DeckListScreen
import com.lumecard.app.ui.screens.study.StudyModeScreen
import com.lumecard.app.ui.screens.study.StudyScreen
import com.lumecard.shared.model.Deck
import org.koin.compose.koinInject

class DashboardScreen : Screen {
    override val key: ScreenKey = "Dashboard"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: DashboardViewModel = koinInject()
        val decks by viewModel.decks.collectAsState()
        val decksWithCount by viewModel.decksWithCount.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        val firstStudyableDeck = decksWithCount.firstOrNull { it.cardCount > 0 }?.deck

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("LumeCard") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(DeckListScreen()) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "管理牌组")
                }
            }
        ) { padding ->
            if (isLoading && decks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "今日学习",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatItem("牌组", "${decks.size}")
                                    val totalCards = decksWithCount.sumOf { it.cardCount }
                                    StatItem("总卡片", "$totalCards")
                                    StatItem("有卡片牌组", "${decksWithCount.count { it.cardCount > 0 }}")
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "快速操作",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "开始学习",
                                subtitle = if (firstStudyableDeck != null) "${decksWithCount.count { it.cardCount > 0 }} 个牌组可用" else "暂无可用卡片",
                                enabled = firstStudyableDeck != null,
                                icon = Icons.Default.PlayArrow,
                                onClick = {
                                    navigator.push(StudyModeScreen())
                                }
                            )
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "管理牌组",
                                icon = Icons.AutoMirrored.Filled.List,
                                onClick = { navigator.push(DeckListScreen()) }
                            )
                        }
                    }

                    item {
                        Text(
                            "我的牌组 (${decks.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (decks.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "开始你的学习之旅",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "创建第一个牌组，添加卡片，开始学习",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(decksWithCount) { item ->
                            DeckItem(
                                deck = item.deck,
                                cardCount = item.cardCount,
                                onClick = { navigator.push(CardListScreen(item.deck.id, item.deck.name)) },
                                onStudy = {
                                    if (item.cardCount > 0) {
                                        navigator.push(StudyScreen(item.deck.id, item.deck.name))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatItem(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    @Composable
    private fun QuickActionCard(
        modifier: Modifier = Modifier,
        title: String,
        subtitle: String? = null,
        enabled: Boolean = true,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit
    ) {
        Card(
            modifier = modifier,
            onClick = onClick,
            enabled = enabled
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    @Composable
    private fun DeckItem(
        deck: Deck,
        cardCount: Int,
        onClick: () -> Unit,
        onStudy: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    deck.icon,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        deck.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "$cardCount 张卡片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val desc = deck.description
                    if (desc != null) {
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onStudy, enabled = cardCount > 0) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "学习",
                        tint = if (cardCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
