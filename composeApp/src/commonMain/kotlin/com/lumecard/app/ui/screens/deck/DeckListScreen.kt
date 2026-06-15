package com.lumecard.app.ui.screens.deck

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
import com.lumecard.app.ui.screens.study.StudyScreen
import com.lumecard.shared.model.Deck
import kotlinx.coroutines.launch
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import org.koin.compose.koinInject

class DeckListScreen : Screen {
    override val key: ScreenKey = "DeckList"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: DeckViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val decks by viewModel.decks.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val sortConfig by viewModel.sortConfig.collectAsState()
        val deckCardCounts by viewModel.deckCardCounts.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var showCreateDialog by remember { mutableStateOf(false) }
        var editingDeck by remember { mutableStateOf<Deck?>(null) }
        var deletingDeck by remember { mutableStateOf<Deck?>(null) }
        var showSortMenu by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.deckListTitle,
                    onBack = { navigator.pop() },
                    action = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Text(strings.actionSort, style = MaterialTheme.typography.labelLarge)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                Text(strings.deckSortTitle, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                HorizontalDivider()
                                SortField.entries.forEach { field ->
                                    val label = when (field) {
                                        SortField.NAME -> strings.deckSortName
                                        SortField.CREATED_AT -> strings.deckSortCreated
                                        SortField.UPDATED_AT -> strings.deckSortModified
                                        SortField.STUDY_TIME -> strings.deckSortStudyTime
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
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.deckCreate)
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (decks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            strings.deckEmpty,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            strings.deckEmptyDesc,
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
                        .padding(horizontal = LumeCardTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(LumeCardTheme.spacing.section)
                ) {
                    item {
                        Text(
                            "${strings.actionSort}: ${sortConfig.field.name} ${if (sortConfig.order == SortOrder.ASC) "↑" else "↓"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(decks, key = { it.id }) { deck ->
                        val cardCount = deckCardCounts[deck.id] ?: 0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                navigator.push(CardListScreen(deck.id, deck.name))
                            }
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
                                        strings.deckCardsCount(cardCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val desc = deck.description
                                    if (!desc.isNullOrBlank()) {
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            navigator.push(StudyScreen(deck.id, deck.name))
                                        },
                                        enabled = cardCount > 0
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = strings.actionLearning,
                                            tint = if (cardCount > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                    }
                                    IconButton(onClick = { editingDeck = deck }) {
                                        Icon(Icons.Default.Edit, contentDescription = strings.actionEdit,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { deletingDeck = deck }) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.actionDelete,
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create dialog
        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text(strings.deckCreate) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(strings.deckName) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(strings.deckDescPlaceholder) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                scope.launch {
                                    viewModel.createDeck(name, description.ifBlank { null })
                                    showCreateDialog = false
                                }
                            }
                        },
                        enabled = name.isNotBlank()
                    ) { Text(strings.actionCreate) }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text(strings.actionCancel) }
                }
            )
        }

        // Edit dialog
        if (editingDeck != null) {
            var name by remember(editingDeck) { mutableStateOf(editingDeck!!.name) }
            var description by remember(editingDeck) { mutableStateOf(editingDeck!!.description ?: "") }

            AlertDialog(
                onDismissRequest = { editingDeck = null },
                title = { Text(strings.deckEdit) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(strings.deckName) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(strings.deckDescPlaceholder) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                scope.launch {
                                    viewModel.updateDeck(editingDeck!!.id, name, description.ifBlank { null })
                                    editingDeck = null
                                }
                            }
                        },
                        enabled = name.isNotBlank()
                    ) { Text(strings.actionSave) }
                },
                dismissButton = {
                    TextButton(onClick = { editingDeck = null }) { Text(strings.actionCancel) }
                }
            )
        }

        // Delete confirmation
        if (deletingDeck != null) {
            AlertDialog(
                onDismissRequest = { deletingDeck = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text(strings.deckConfirmDelete) },
                text = {
                    Text(strings.deckDeleteConfirmText(deletingDeck!!.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.deleteDeck(deletingDeck!!.id)
                                deletingDeck = null
                                snackbarHostState.showSnackbar(strings.deckDeleted)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(strings.actionDelete) }
                },
                dismissButton = {
                    TextButton(onClick = { deletingDeck = null }) { Text(strings.actionCancel) }
                }
            )
        }
    }
}



