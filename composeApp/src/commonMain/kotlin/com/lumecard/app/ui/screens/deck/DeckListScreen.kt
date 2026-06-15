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
import org.koin.compose.koinInject

class DeckListScreen : Screen {
    override val key: ScreenKey = "DeckList"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: DeckViewModel = koinInject()
        val decks by viewModel.decks.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val sortConfig by viewModel.sortConfig.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var showCreateDialog by remember { mutableStateOf(false) }
        var editingDeck by remember { mutableStateOf<Deck?>(null) }
        var deletingDeck by remember { mutableStateOf<Deck?>(null) }
        var showSortMenu by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("牌组列表") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Text("排序", style = MaterialTheme.typography.labelLarge)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                Text("排序方式", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                HorizontalDivider()
                                SortField.entries.forEach { field ->
                                    SortOrder.entries.forEach { order ->
                                        val label = when (field) {
                                            SortField.NAME -> "名称 ${if (order == SortOrder.ASC) "↑" else "↓"}"
                                            SortField.CREATED_AT -> "创建时间 ${if (order == SortOrder.ASC) "↑" else "↓"}"
                                            SortField.UPDATED_AT -> "修改时间 ${if (order == SortOrder.ASC) "↑" else "↓"}"
                                            SortField.STUDY_TIME -> "学习时长 ${if (order == SortOrder.ASC) "↑" else "↓"}"
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setSortConfig(SortConfig(field, order))
                                                showSortMenu = false
                                            },
                                            leadingIcon = {
                                                if (sortConfig.field == field && sortConfig.order == order) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
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
            } else if (decks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "还没有牌组",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击 + 创建第一个牌组",
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
                            "排序: ${sortConfig.field.name} ${if (sortConfig.order == SortOrder.ASC) "↑" else "↓"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(decks, key = { it.id }) { deck ->
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
                                    IconButton(onClick = {
                                        navigator.push(StudyScreen(deck.id, deck.name))
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "学习",
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { editingDeck = deck }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { deletingDeck = deck }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除",
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
                title = { Text("创建牌组") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("牌组名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("描述（可选）") },
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
                    ) { Text("创建") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
                }
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
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("牌组名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("描述（可选）") },
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
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { editingDeck = null }) { Text("取消") }
                }
            )
        }

        // Delete confirmation
        if (deletingDeck != null) {
            AlertDialog(
                onDismissRequest = { deletingDeck = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("确认删除") },
                text = {
                    Text("确定要删除牌组「${deletingDeck!!.name}」吗？\n牌组中的所有卡片也将被删除。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.deleteDeck(deletingDeck!!.id)
                                deletingDeck = null
                                snackbarHostState.showSnackbar("牌组已删除")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingDeck = null }) { Text("取消") }
                }
            )
        }
    }
}
