package com.lumecard.app.ui.screens.knowledgebase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.screens.deck.DeckListScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.model.KnowledgeBase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class KnowledgeBaseScreen : Screen {
    override val key: ScreenKey = "KnowledgeBase"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: KnowledgeBaseViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val knowledgeBases by viewModel.knowledgeBases.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val scope = rememberCoroutineScope()
        var showCreateDialog by remember { mutableStateOf(false) }
        var editingKb by remember { mutableStateOf<KnowledgeBase?>(null) }
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            viewModel.loadKnowledgeBases()
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.kbTitle,
                    onBack = { navigator.pop() },
                    action = {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = strings.kbCreate)
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (knowledgeBases.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(spacing.md))
                        Text(strings.kbEmpty, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(spacing.sm))
                        FilledTonalButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.sm))
                            Text(strings.kbCreate)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(knowledgeBases, key = { it.id }) { kb ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = radius.card,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                            onClick = { navigator.push(DeckListScreen(knowledgeBaseId = kb.id, knowledgeBaseName = kb.name)) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = radius.button,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Spacer(Modifier.width(spacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(kb.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    val desc = kb.description
                                    if (desc != null) {
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { editingKb = kb }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { deleteConfirmId = kb.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            KnowledgeBaseDialog(
                title = strings.kbCreate,
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, desc ->
                    showCreateDialog = false
                    scope.launch { viewModel.createKnowledgeBase(name, desc) }
                }
            )
        }

        if (editingKb != null) {
            val currentKb = editingKb!!
            KnowledgeBaseDialog(
                title = strings.kbEdit,
                initialName = currentKb.name,
                initialDescription = currentKb.description,
                onDismiss = { editingKb = null },
                onConfirm = { name, desc ->
                    editingKb = null
                    scope.launch { viewModel.updateKnowledgeBase(currentKb.id, name, desc) }
                }
            )
        }

        if (deleteConfirmId != null) {
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                title = { Text(strings.kbDeleteConfirm) },
                text = { Text(strings.kbDeleteConfirmDesc) },
                confirmButton = {
                    TextButton(onClick = {
                        val id = deleteConfirmId!!
                        deleteConfirmId = null
                        scope.launch { viewModel.deleteKnowledgeBase(id) }
                    }) { Text(strings.actionConfirm) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmId = null }) { Text(strings.actionCancel) }
                }
            )
        }
    }
}

@Composable
private fun KnowledgeBaseDialog(
    title: String,
    initialName: String = "",
    initialDescription: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description.ifBlank { null }) }, enabled = name.isNotBlank()) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
