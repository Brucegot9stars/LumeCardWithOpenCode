package com.lumecard.app.ui.screens.deck

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.components.ConfirmOperationDialog
import com.lumecard.app.ui.components.OperationConfirmationManager
import com.lumecard.app.ui.screens.study.StudyScreen
import com.lumecard.shared.data.EntityMergeManager
import com.lumecard.shared.data.EntityOperationType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import org.koin.compose.koinInject

class DeckListScreen(
    private val knowledgeBaseId: String = "default",
    private val knowledgeBaseName: String = "Default"
) : Screen {
    override val key: ScreenKey = "DeckList_$knowledgeBaseId"

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
        val mergeManager: EntityMergeManager = koinInject()
        val confirmManager: OperationConfirmationManager = koinInject()
        val kbRepository: KnowledgeBaseRepository = koinInject()
        val allKbs by kbRepository.getAll().collectAsState(initial = emptyList())

        LaunchedEffect(knowledgeBaseId) {
            viewModel.loadDecks(knowledgeBaseId)
        }

        var showCreateDialog by remember { mutableStateOf(false) }
        var editingDeck by remember { mutableStateOf<Deck?>(null) }
        var deletingDeck by remember { mutableStateOf<Deck?>(null) }
        var showSortMenu by remember { mutableStateOf(false) }

        // Move state
        var movingDeckId by remember { mutableStateOf<String?>(null) }
        var showMoveKbPicker by remember { mutableStateOf(false) }
        var mergeResultMessage by remember { mutableStateOf<String?>(null) }

        // Drag state
        var draggingDeckId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var dropTargetDeckId by remember { mutableStateOf<String?>(null) }
        val itemPositions = remember { mutableStateMapOf<String, Offset>() }
        val itemSizes = remember { mutableStateMapOf<String, IntSize>() }

        // Merge state
        var pendingMergeSourceId by remember { mutableStateOf<String?>(null) }
        var pendingMergeTargetId by remember { mutableStateOf<String?>(null) }
        var showMergeConfirm by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.deckListTitle(knowledgeBaseName),
                    onBack = { navigator.pop() },
                    action = {
                        if (draggingDeckId != null) {
                            IconButton(onClick = {
                                draggingDeckId = null
                                dragOffset = Offset.Zero
                                dropTargetDeckId = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = strings.actionCancel)
                            }
                        } else {
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
                        }
                    },
                )
            },
            floatingActionButton = {
                if (draggingDeckId == null) {
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.deckCreate)
                    }
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
                            "${strings.deckSortByLabel(sortConfig.field.name)} ${if (sortConfig.order == SortOrder.ASC) "↑" else "↓"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(decks, key = { it.id }) { deck ->
                        val cardCount = deckCardCounts[deck.id] ?: 0
                        val isDragSource = draggingDeckId == deck.id
                        val isDropTarget = dropTargetDeckId == deck.id
                        val bgColor by animateColorAsState(
                            if (isDragSource) MaterialTheme.colorScheme.tertiaryContainer
                            else if (isDropTarget) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    itemPositions[deck.id] = coords.positionInWindow()
                                    itemSizes[deck.id] = coords.size
                                }
                                .then(
                                    if (isDragSource) Modifier.offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                                    else Modifier
                                )
                                .graphicsLayer {
                                    if (isDragSource) {
                                        shadowElevation = 12f
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        alpha = 0.9f
                                    }
                                }
                                .clickable(enabled = draggingDeckId == null) {
                                    navigator.push(CardListScreen(deck.id, deck.name))
                                }
                                .pointerInput(deck.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingDeckId = deck.id
                                            dragOffset = Offset.Zero
                                            dropTargetDeckId = null
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            val pos = itemPositions[deck.id] ?: return@detectDragGesturesAfterLongPress
                                            val cursorPos = pos + dragOffset
                                            dropTargetDeckId = findDropTarget(cursorPos, itemPositions, itemSizes, deck.id)
                                        },
                                        onDragEnd = {
                                            val srcId = draggingDeckId
                                            val tgtId = dropTargetDeckId
                                            if (tgtId != null && srcId != null && tgtId != srcId) {
                                                pendingMergeSourceId = srcId
                                                pendingMergeTargetId = tgtId
                                                showMergeConfirm = true
                                            }
                                            draggingDeckId = null
                                            dragOffset = Offset.Zero
                                            dropTargetDeckId = null
                                        },
                                        onDragCancel = {
                                            draggingDeckId = null
                                            dragOffset = Offset.Zero
                                            dropTargetDeckId = null
                                        },
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isDragSource) 12.dp else 2.dp,
                            ),
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
                                    if (isDropTarget) {
                                        Text(
                                            strings.actionMerge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (draggingDeckId == null) {
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
                                        Box {
                                            var expanded by remember { mutableStateOf(false) }
                                            IconButton(onClick = { expanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.actionMove) },
                                                    onClick = {
                                                        expanded = false
                                                        movingDeckId = deck.id
                                                        showMoveKbPicker = true
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.DriveFileMove, contentDescription = null)
                                                    },
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

        // Merge confirmation dialog (from drag-drop)
        if (showMergeConfirm && pendingMergeSourceId != null && pendingMergeTargetId != null) {
            val sourceDeck = decks.find { it.id == pendingMergeSourceId }
            val targetDeck = decks.find { it.id == pendingMergeTargetId }

            if (sourceDeck != null && targetDeck != null) {
                ConfirmOperationDialog(
                    title = strings.mergeDeckTitle,
                    text = strings.mergeDeckConfirm(sourceDeck.name, targetDeck.name),
                    operationType = EntityOperationType.DECK_MERGE,
                    confirmationManager = confirmManager,
                    snoozeLabel = strings.confirmNoPrompt60s,
                    onConfirm = {
                        showMergeConfirm = false
                        val sId = pendingMergeSourceId!!
                        val tId = pendingMergeTargetId!!
                        pendingMergeSourceId = null
                        pendingMergeTargetId = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                mergeManager.mergeDecks(sId, tId)
                            }
                            result.fold(
                                onSuccess = { r ->
                                    mergeResultMessage = strings.moveMergeResult(r.itemsMoved, r.conflictsResolved)
                                },
                                onFailure = { e ->
                                    mergeResultMessage = e.message
                                },
                            )
                            viewModel.loadDecks(knowledgeBaseId)
                        }
                    },
                    onDismiss = {
                        showMergeConfirm = false
                        pendingMergeSourceId = null
                        pendingMergeTargetId = null
                    },
                )
            }
        }

        // Merge/Move result dialog
        if (mergeResultMessage != null) {
            AlertDialog(
                onDismissRequest = { mergeResultMessage = null },
                title = { Text(strings.moveMergeSuccess) },
                text = { Text(mergeResultMessage ?: "") },
                confirmButton = {
                    TextButton(onClick = { mergeResultMessage = null }) {
                        Text(strings.actionOk)
                    }
                },
            )
        }

        // Move to KB dialog
        if (showMoveKbPicker && movingDeckId != null) {
            val deckToMove = decks.find { it.id == movingDeckId }
            val otherKbs = allKbs.filter { it.id != knowledgeBaseId }

            AlertDialog(
                onDismissRequest = { showMoveKbPicker = false; movingDeckId = null },
                title = { Text(strings.moveDeckTitle) },
                text = {
                    if (otherKbs.isEmpty()) {
                        Text(strings.moveDeckNoTarget)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            otherKbs.forEach { kb ->
                                TextButton(
                                    onClick = {
                                        val deckId = movingDeckId!!
                                        showMoveKbPicker = false
                                        movingDeckId = null
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                mergeManager.moveDeck(deckId, kb.id)
                                            }
                                            result.fold(
                                                onSuccess = { r ->
                                                    mergeResultMessage = strings.moveMergeResult(r.itemsMoved, r.conflictsResolved)
                                                },
                                                onFailure = { e ->
                                                    mergeResultMessage = e.message
                                                },
                                            )
                                            viewModel.loadDecks(knowledgeBaseId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(kb.name, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMoveKbPicker = false; movingDeckId = null }) {
                        Text(strings.actionCancel)
                    }
                },
            )
        }
    }
}

private fun findDropTarget(
    cursorPos: Offset,
    positions: Map<String, Offset>,
    sizes: Map<String, IntSize>,
    excludeId: String,
): String? {
    return positions.entries.firstOrNull { (id, pos) ->
        if (id == excludeId) return@firstOrNull false
        val size = sizes[id] ?: return@firstOrNull false
        cursorPos.x >= pos.x && cursorPos.x <= pos.x + size.width &&
        cursorPos.y >= pos.y && cursorPos.y <= pos.y + size.height
    }?.key
}


