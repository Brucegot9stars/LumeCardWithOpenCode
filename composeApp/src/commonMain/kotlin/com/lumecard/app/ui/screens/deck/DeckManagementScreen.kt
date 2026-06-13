package com.lumecard.app.ui.screens.deck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.shared.model.Deck
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class DeckManagementScreen : Screen {
    override val key: ScreenKey = "DeckManagement"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: DeckViewModel = koinInject()
        val decks by viewModel.decks.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var showCreateDialog by remember { mutableStateOf(false) }
        var editingDeck by remember { mutableStateOf<Deck?>(null) }
        var deletingDeck by remember { mutableStateOf<Deck?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("牌组管理") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "创建牌组")
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
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(decks, key = { it.id }) { deck ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(deck.icon, style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(deck.name, style = MaterialTheme.typography.titleMedium)
                                        val desc = deck.description
                                        if (!desc.isNullOrBlank()) {
                                            Text(desc, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextButton(onClick = {
                                        navigator.push(CardListScreen(deck.id, deck.name))
                                    }) {
                                        Icon(Icons.Default.Info, contentDescription = null,
                                            modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("查看卡片")
                                    }
                                    TextButton(onClick = { editingDeck = deck }) {
                                        Icon(Icons.Default.Edit, contentDescription = null,
                                            modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("编辑")
                                    }
                                    TextButton(onClick = { deletingDeck = deck }) {
                                        Icon(Icons.Default.Delete, contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(4.dp))
                                        Text("删除", color = MaterialTheme.colorScheme.error)
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
                title = { Text("创建牌组") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it },
                            label = { Text("牌组名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = description, onValueChange = { description = it },
                            label = { Text("描述（可选）") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            scope.launch { viewModel.createDeck(name, description.ifBlank { null }); showCreateDialog = false }
                        }
                    }, enabled = name.isNotBlank()) { Text("创建") }
                },
                dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } }
            )
        }

        // Edit dialog
        if (editingDeck != null) {
            var name by remember(editingDeck) { mutableStateOf(editingDeck!!.name) }
            var description by remember(editingDeck) { mutableStateOf(editingDeck!!.description ?: "") }
            AlertDialog(
                onDismissRequest = { editingDeck = null },
                title = { Text("编辑牌组") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it },
                            label = { Text("牌组名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = description, onValueChange = { description = it },
                            label = { Text("描述（可选）") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            scope.launch { viewModel.updateDeck(editingDeck!!.id, name, description.ifBlank { null }); editingDeck = null }
                        }
                    }, enabled = name.isNotBlank()) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { editingDeck = null }) { Text("取消") } }
            )
        }

        // Delete confirmation
        if (deletingDeck != null) {
            AlertDialog(
                onDismissRequest = { deletingDeck = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("确认删除") },
                text = { Text("确定要删除牌组「${deletingDeck!!.name}」吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            viewModel.deleteDeck(deletingDeck!!.id)
                            deletingDeck = null
                            snackbarHostState.showSnackbar("牌组已删除")
                        }
                    }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { deletingDeck = null }) { Text("取消") } }
            )
        }
    }
}
