package com.lumecard.app.ui.screens.deck

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.screens.card.CardViewModel
import com.lumecard.app.ui.screens.card.CreateCardScreen
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.screens.study.StudyScreen
import com.lumecard.shared.model.Card
import org.koin.compose.koinInject

class CardListScreen(
    private val deckId: String,
    private val deckName: String
) : Screen {
    override val key: ScreenKey = "CardList_$deckId"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: CardViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val cards by viewModel.cards.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val sortConfig by viewModel.sortConfig.collectAsState()
        var showSortMenu by remember { mutableStateOf(false) }

        LaunchedEffect(deckId) {
            viewModel.loadSortPref()
            viewModel.loadCards(deckId)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(deckName) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.actionBack)
                        }
                    },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Text(strings.actionSort, style = MaterialTheme.typography.labelLarge)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                Text(strings.cardSortTitle, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                HorizontalDivider()
                                SortField.entries.forEach { field ->
                                    val label = when (field) {
                                        SortField.NAME -> strings.cardSortName
                                        SortField.CREATED_AT -> strings.cardSortCreated
                                        SortField.UPDATED_AT -> strings.cardSortModified
                                        SortField.STUDY_TIME -> strings.cardSortStudyTime
                                    }
                                    val isActive = sortConfig.field == field
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            if (isActive) {
                                                val newOrder = if (sortConfig.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
                                                viewModel.setSortConfig(SortConfig(field, newOrder))
                                            } else {
                                                viewModel.setSortConfig(SortConfig(field, SortOrder.ASC))
                                            }
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (isActive) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        trailingIcon = {
                                            if (isActive) {
                                                Text(
                                                    if (sortConfig.order == SortOrder.ASC) "\u2191" else "\u2193",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { navigator.push(StudyScreen(deckId, deckName)) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = strings.actionLearning)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(CreateCardScreen(deckId, deckName)) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.cardAdd)
                }
            }
        ) { padding ->
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            strings.cardEmpty,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            strings.cardEmptyDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "${strings.actionSort}: ${sortConfig.field.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(cards) { card ->
                        CardItem(
                            card = card,
                            onEdit = { navigator.push(CreateCardScreen(deckId, deckName, editCard = card)) },
                            onDelete = { viewModel.deleteCard(card.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardItem(
    card: Card,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = koinInject<I18nManager>().strings
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        card.type.name.take(2),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.front,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        card.tags.take(3).forEach { tag ->
                            SuggestionChip(
                                onClick = { },
                                label = {
                                    Text(
                                        tag,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = strings.actionEdit,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.actionDelete,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
