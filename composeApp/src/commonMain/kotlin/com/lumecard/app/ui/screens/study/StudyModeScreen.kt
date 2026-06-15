package com.lumecard.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.screens.deck.DeckViewModel
import com.lumecard.shared.model.Deck
import org.koin.compose.koinInject

enum class StudyMode(val label: String, val description: String) {
    MIXED("混合牌组", "从所有牌组中随机抽取卡片"),
    SINGLE("单牌组", "仅学习某一个牌组"),
    MULTI("多牌组", "选择多个牌组进行学习")
}

class StudyModeScreen : Screen {
    override val key: ScreenKey = "StudyMode"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val deckViewModel: DeckViewModel = koinInject()
        val decks by deckViewModel.decks.collectAsState()
        val deckCardCounts by deckViewModel.deckCardCounts.collectAsState()

        var selectedMode by remember { mutableStateOf(StudyMode.MIXED) }
        var selectedDeckIds by remember { mutableStateOf(setOf<String>()) }

        val studyableDecks = decks.filter { (deckCardCounts[it.id] ?: 0) > 0 }

        LaunchedEffect(Unit) {
            deckViewModel.loadDecks()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("学习模式") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            bottomBar = {
                val enabled = when (selectedMode) {
                    StudyMode.MIXED -> studyableDecks.isNotEmpty()
                    StudyMode.SINGLE -> selectedDeckIds.size == 1
                    StudyMode.MULTI -> selectedDeckIds.isNotEmpty()
                }
                val totalCards = when (selectedMode) {
                    StudyMode.MIXED -> studyableDecks.sumOf { deckCardCounts[it.id] ?: 0 }
                    StudyMode.SINGLE, StudyMode.MULTI -> selectedDeckIds.sumOf { deckCardCounts[it] ?: 0 }
                }
                Surface(
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "共 $totalCards 张卡片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val deckIds = when (selectedMode) {
                                    StudyMode.MIXED -> studyableDecks.map { it.id }
                                    StudyMode.SINGLE, StudyMode.MULTI -> selectedDeckIds.toList()
                                }
                                val name = when (selectedMode) {
                                    StudyMode.MIXED -> "混合学习"
                                    StudyMode.SINGLE -> {
                                        val deck = studyableDecks.find { it.id in selectedDeckIds }
                                        deck?.name ?: "单牌组学习"
                                    }
                                    StudyMode.MULTI -> "多牌组学习"
                                }
                                navigator.push(StudyScreen(deckIds, name))
                            },
                            enabled = enabled
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("开始学习")
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "选择学习模式",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(StudyMode.entries) { mode ->
                    val count = when (mode) {
                        StudyMode.MIXED -> studyableDecks.sumOf { deckCardCounts[it.id] ?: 0 }
                        StudyMode.SINGLE, StudyMode.MULTI -> 0
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedMode = mode
                            if (mode == StudyMode.SINGLE) {
                                selectedDeckIds = emptySet()
                            } else if (mode == StudyMode.MULTI) {
                                selectedDeckIds = emptySet()
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedMode == mode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMode == mode,
                                onClick = {
                                    selectedMode = mode
                                    if (mode == StudyMode.SINGLE) {
                                        selectedDeckIds = emptySet()
                                    } else if (mode == StudyMode.MULTI) {
                                        selectedDeckIds = emptySet()
                                    }
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (mode == StudyMode.MIXED && count > 0) {
                                Text(
                                    "$count 张",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (selectedMode == StudyMode.SINGLE || selectedMode == StudyMode.MULTI) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "选择牌组",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (studyableDecks.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "暂无可用牌组",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(studyableDecks, key = { it.id }) { deck ->
                            val cardCount = deckCardCounts[deck.id] ?: 0
                            val isSelected = deck.id in selectedDeckIds
                            val canSelect = if (selectedMode == StudyMode.SINGLE) !isSelected || selectedDeckIds.size <= 1 else true

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (selectedMode == StudyMode.SINGLE) {
                                        selectedDeckIds = setOf(deck.id)
                                    } else {
                                        selectedDeckIds = if (isSelected) {
                                            selectedDeckIds - deck.id
                                        } else {
                                            selectedDeckIds + deck.id
                                        }
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedMode == StudyMode.MULTI) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedDeckIds = if (checked) {
                                                    selectedDeckIds + deck.id
                                                } else {
                                                    selectedDeckIds - deck.id
                                                }
                                            }
                                        )
                                    } else {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedDeckIds = setOf(deck.id)
                                            }
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(deck.icon, style = MaterialTheme.typography.headlineSmall)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(deck.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "$cardCount 张卡片",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
