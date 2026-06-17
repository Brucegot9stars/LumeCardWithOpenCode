package com.lumecard.app.ui.screens.warehouse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardDialog
import com.lumecard.app.ui.components.LumeCardTextField
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.model.CardType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class WarehouseScreen : Screen {
    override val key: ScreenKey = "Warehouse"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: WarehouseViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val treeNodes by viewModel.treeNodes.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val selectedIds by viewModel.selectedIds.collectAsState()
        val searchQuery by viewModel.searchQuery.collectAsState()
        val scope = rememberCoroutineScope()

        var showCreateDialog by remember { mutableStateOf(false) }
        var createType by remember { mutableStateOf(NodeType.KNOWLEDGE_BASE) }
        var createParentId by remember { mutableStateOf<String?>(null) }
        var dialogName by remember { mutableStateOf("") }
        var dialogDesc by remember { mutableStateOf("") }
        var editingNode by remember { mutableStateOf<TreeNode?>(null) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var deleteTargetId by remember { mutableStateOf<String?>(null) }

        val isSelectMode = selectedIds.isNotEmpty()

        Scaffold(
            topBar = {
                if (isSelectMode) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.Check, contentDescription = "Select All")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                } else {
                    LumeCardTopBar(
                        title = strings.warehouseTitle,
                        onBack = { navigator.pop() },
                        action = {
                            IconButton(onClick = {
                                createType = NodeType.KNOWLEDGE_BASE
                                createParentId = null
                                dialogName = ""
                                dialogDesc = ""
                                showCreateDialog = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = strings.warehouseAdd)
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(strings.warehouseSearch) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
                )

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (treeNodes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(spacing.md))
                            Text(strings.warehouseEmpty, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(spacing.sm))
                            FilledTonalButton(onClick = {
                                createType = NodeType.KNOWLEDGE_BASE
                                createParentId = null
                                dialogName = ""
                                dialogDesc = ""
                                showCreateDialog = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(spacing.sm))
                                Text(strings.warehouseCreateKB)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(treeNodes, key = { it.id }) { node ->
                            TreeNodeItem(
                                node = node,
                                isSelected = node.id in selectedIds,
                                isSelectMode = isSelectMode,
                                onToggleExpand = { viewModel.toggleExpand(node.id) },
                                onToggleSelect = { viewModel.toggleSelect(node.id) },
                                onAddDeck = {
                                    createType = NodeType.DECK
                                    createParentId = node.id
                                    dialogName = ""
                                    dialogDesc = ""
                                    showCreateDialog = true
                                },
                                onEdit = {
                                    editingNode = node
                                    dialogName = node.name
                                    dialogDesc = ""
                                    showCreateDialog = true
                                },
                                onDelete = {
                                    deleteTargetId = node.id
                                    showDeleteConfirm = true
                                },
                                onAddCard = null,
                                onEditCard = null,
                                onDeleteCard = null,
                                spacing = spacing,
                                radius = radius,
                            )
                            AnimatedVisibility(
                                visible = node.isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    node.children.forEach { child ->
                                        TreeNodeItem(
                                            node = child,
                                            isSelected = child.id in selectedIds,
                                            isSelectMode = isSelectMode,
                                            onToggleExpand = { viewModel.toggleExpand(child.id) },
                                            onToggleSelect = { viewModel.toggleSelect(child.id) },
                                            onAddDeck = null,
                                            onEdit = {
                                                editingNode = child
                                                dialogName = child.name
                                                dialogDesc = ""
                                                showCreateDialog = true
                                            },
                                            onDelete = {
                                                deleteTargetId = child.id
                                                showDeleteConfirm = true
                                            },
                                            onAddCard = if (child.type == NodeType.DECK) {
                                                {
                                                    createType = NodeType.CARD
                                                    createParentId = child.id
                                                    dialogName = ""
                                                    dialogDesc = ""
                                                    showCreateDialog = true
                                                }
                                            } else null,
                                            onEditCard = if (child.type == NodeType.CARD) {
                                                {
                                                    editingNode = child
                                                    dialogName = child.name
                                                    dialogDesc = ""
                                                    showCreateDialog = true
                                                }
                                            } else null,
                                            onDeleteCard = if (child.type == NodeType.CARD) {
                                                {
                                                    deleteTargetId = child.id
                                                    showDeleteConfirm = true
                                                }
                                            } else null,
                                            spacing = spacing,
                                            radius = radius,
                                        )
                                        AnimatedVisibility(
                                            visible = child.isExpanded && child.type == NodeType.DECK,
                                            enter = expandVertically(),
                                            exit = shrinkVertically(),
                                        ) {
                                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                                child.children.forEach { card ->
                                                    TreeNodeItem(
                                                        node = card,
                                                        isSelected = card.id in selectedIds,
                                                        isSelectMode = isSelectMode,
                                                        onToggleExpand = {},
                                                        onToggleSelect = { viewModel.toggleSelect(card.id) },
                                                        onAddDeck = null,
                                                        onEdit = {
                                                            editingNode = card
                                                            dialogName = card.name
                                                            dialogDesc = ""
                                                            showCreateDialog = true
                                                        },
                                                        onDelete = {
                                                            deleteTargetId = card.id
                                                            showDeleteConfirm = true
                                                        },
                                                        onAddCard = null,
                                                        onEditCard = null,
                                                        onDeleteCard = null,
                                                        spacing = spacing,
                                                        radius = radius,
                                                    )
                                                }
                                            }
                                        }
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
            val title = when {
                editingNode != null -> strings.warehouseEdit
                createType == NodeType.KNOWLEDGE_BASE -> strings.warehouseCreateKB
                createType == NodeType.DECK -> strings.warehouseCreateDeck
                createType == NodeType.CARD -> strings.warehouseCreateCard
                else -> strings.warehouseAdd
            }
            LumeCardDialog(
                title = title,
                onDismiss = { showCreateDialog = false; editingNode = null },
                onConfirm = {
                    scope.launch {
                        if (editingNode != null) {
                            when (editingNode!!.type) {
                                NodeType.KNOWLEDGE_BASE -> viewModel.updateKnowledgeBase(editingNode!!.id, dialogName, dialogDesc.ifBlank { null })
                                NodeType.DECK -> viewModel.updateDeck(editingNode!!.id, dialogName, dialogDesc.ifBlank { null })
                                NodeType.CARD -> viewModel.updateCard(editingNode!!.id, dialogName, dialogDesc)
                            }
                        } else {
                            when (createType) {
                                NodeType.KNOWLEDGE_BASE -> viewModel.createKnowledgeBase(dialogName, dialogDesc.ifBlank { null })
                                NodeType.DECK -> viewModel.createDeck(createParentId!!, dialogName, dialogDesc.ifBlank { null })
                                NodeType.CARD -> viewModel.createCard(createParentId!!, dialogName, dialogDesc)
                            }
                        }
                        showCreateDialog = false
                        editingNode = null
                    }
                },
                confirmText = strings.actionSave,
                confirmEnabled = dialogName.isNotBlank(),
            ) {
                LumeCardTextField(value = dialogName, onValueChange = { dialogName = it }, label = strings.fieldName)
                LumeCardTextField(
                    value = dialogDesc,
                    onValueChange = { dialogDesc = it },
                    label = if (createType == NodeType.CARD || editingNode?.type == NodeType.CARD) strings.warehouseCardContent else strings.fieldDescription,
                    singleLine = false,
                )
            }
        }

        // Delete confirm
        if (showDeleteConfirm) {
            LumeCardDialog(
                title = strings.warehouseDeleteConfirm,
                onDismiss = { showDeleteConfirm = false; deleteTargetId = null },
                onConfirm = {
                    scope.launch {
                        if (isSelectMode) {
                            viewModel.batchDelete()
                        } else if (deleteTargetId != null) {
                            val id = deleteTargetId!!
                            val node = treeNodes.flatMap { listOf(it) + it.children + it.children.flatMap { c -> c.children } }.find { it.id == id }
                            when (node?.type) {
                                NodeType.KNOWLEDGE_BASE -> viewModel.deleteKnowledgeBase(id)
                                NodeType.DECK -> viewModel.deleteDeck(id)
                                NodeType.CARD -> viewModel.deleteCard(id)
                                else -> {}
                            }
                        }
                        showDeleteConfirm = false
                        deleteTargetId = null
                    }
                },
                confirmText = strings.actionConfirm,
            ) {
                Text(strings.warehouseDeleteDesc, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TreeNodeItem(
    node: TreeNode,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onAddDeck: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddCard: (() -> Unit)?,
    onEditCard: (() -> Unit)?,
    onDeleteCard: (() -> Unit)?,
    spacing: com.lumecard.app.ui.theme.LumeCardSpacing,
    radius: com.lumecard.app.ui.theme.LumeCardRadius,
) {
    val icon = when (node.type) {
        NodeType.KNOWLEDGE_BASE -> Icons.Default.Star
        NodeType.DECK -> Icons.Default.List
        NodeType.CARD -> Icons.Default.Create
    }
    val iconTint = when (node.type) {
        NodeType.KNOWLEDGE_BASE -> MaterialTheme.colorScheme.primary
        NodeType.DECK -> MaterialTheme.colorScheme.tertiary
        NodeType.CARD -> MaterialTheme.colorScheme.secondary
    }
    val childCount = node.children.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = radius.card,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                )
            }

            if (node.type != NodeType.CARD && childCount > 0) {
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (node.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(32.dp))
            }

            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (childCount > 0) {
                    Text(
                        "$childCount items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!isSelectMode) {
                if (onAddDeck != null) {
                    IconButton(onClick = onAddDeck, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    }
                }
                if (onAddCard != null) {
                    IconButton(onClick = onAddCard, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add Card", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
