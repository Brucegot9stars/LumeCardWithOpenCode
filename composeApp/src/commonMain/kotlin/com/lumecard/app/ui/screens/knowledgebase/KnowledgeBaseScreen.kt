package com.lumecard.app.ui.screens.knowledgebase

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.ConfirmOperationDialog
import com.lumecard.app.ui.components.LumeCardDialog
import com.lumecard.app.ui.components.LumeCardTextField
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.components.OperationConfirmationManager
import com.lumecard.app.ui.screens.deck.DeckListScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.EntityMergeManager
import com.lumecard.shared.data.EntityOperationType
import com.lumecard.shared.model.KnowledgeBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt

class KnowledgeBaseScreen : Screen {
    override val key: ScreenKey = "KnowledgeBase"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: KnowledgeBaseViewModel = koinInject()
        val i18nManager = koinInject<I18nManager>()
        val strings = i18nManager.strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val knowledgeBases by viewModel.knowledgeBases.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val scope = rememberCoroutineScope()
        val mergeManager: EntityMergeManager = koinInject()
        val confirmManager: OperationConfirmationManager = koinInject()
        var showCreateDialog by remember { mutableStateOf(false) }
        var editingKb by remember { mutableStateOf<KnowledgeBase?>(null) }
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }

        // Drag state
        var draggedKbId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var dropTargetKbId by remember { mutableStateOf<String?>(null) }
        val itemPositions = remember { mutableStateMapOf<String, Offset>() }
        val itemSizes = remember { mutableStateMapOf<String, IntSize>() }

        // Merge state
        var pendingMergeSourceId by remember { mutableStateOf<String?>(null) }
        var pendingMergeTargetId by remember { mutableStateOf<String?>(null) }
        var showMergeConfirm by remember { mutableStateOf(false) }
        var mergeResultMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            viewModel.loadKnowledgeBases()
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.kbTitle,
                    onBack = { navigator.pop() },
                    action = {
                        if (draggedKbId != null) {
                            IconButton(onClick = {
                                draggedKbId = null
                                dragOffset = Offset.Zero
                                dropTargetKbId = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = strings.actionCancel)
                            }
                        } else {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = strings.kbCreate)
                            }
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
                        val isDragSource = draggedKbId == kb.id
                        val isDropTarget = dropTargetKbId == kb.id
                        val bgColor by animateColorAsState(
                            if (isDragSource) MaterialTheme.colorScheme.tertiaryContainer
                            else if (isDropTarget) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    itemPositions[kb.id] = coords.positionInWindow()
                                    itemSizes[kb.id] = coords.size
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
                                .clickable(enabled = draggedKbId == null) {
                                    navigator.push(DeckListScreen(knowledgeBaseId = kb.id, knowledgeBaseName = kb.name))
                                }
                                .pointerInput(kb.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedKbId = kb.id
                                            dragOffset = Offset.Zero
                                            dropTargetKbId = null
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            val pos = itemPositions[kb.id] ?: return@detectDragGesturesAfterLongPress
                                            val cursorPos = pos + dragOffset
                                            dropTargetKbId = findDropTarget(cursorPos, itemPositions, itemSizes, kb.id)
                                        },
                                        onDragEnd = {
                                            val srcId = draggedKbId
                                            val tgtId = dropTargetKbId
                                            if (tgtId != null && srcId != null && tgtId != srcId) {
                                                if (confirmManager.isConfirmationNeeded(EntityOperationType.KB_MERGE)) {
                                                    pendingMergeSourceId = srcId
                                                    pendingMergeTargetId = tgtId
                                                    showMergeConfirm = true
                                                } else {
                                                    scope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            mergeManager.mergeKnowledgeBases(srcId, tgtId)
                                                        }
                                                        result.fold(
                                                            onSuccess = { r ->
                                                                mergeResultMessage = strings.moveMergeResult(r.itemsMoved, r.conflictsResolved)
                                                            },
                                                            onFailure = { e ->
                                                                mergeResultMessage = e.message
                                                            },
                                                        )
                                                        viewModel.loadKnowledgeBases()
                                                    }
                                                }
                                            }
                                            draggedKbId = null
                                            dragOffset = Offset.Zero
                                            dropTargetKbId = null
                                        },
                                        onDragCancel = {
                                            draggedKbId = null
                                            dragOffset = Offset.Zero
                                            dropTargetKbId = null
                                        },
                                    )
                                },
                            shape = radius.card,
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isDragSource) 12.dp else 2.dp,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = radius.button,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Spacer(Modifier.width(spacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(kb.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    val desc = kb.description
                                    if (desc != null) {
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isDropTarget) {
                                        Text(
                                            strings.actionMerge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (draggedKbId == null) {
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
        }

        // Merge confirmation dialog (from drag-drop)
        if (showMergeConfirm && pendingMergeSourceId != null && pendingMergeTargetId != null) {
            val sourceKb = knowledgeBases.find { it.id == pendingMergeSourceId }
            val targetKb = knowledgeBases.find { it.id == pendingMergeTargetId }

            if (sourceKb != null && targetKb != null) {
                ConfirmOperationDialog(
                    title = strings.mergeKbTitle,
                    text = strings.mergeKbConfirm(sourceKb.name, targetKb.name),
                    operationType = EntityOperationType.KB_MERGE,
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
                                mergeManager.mergeKnowledgeBases(sId, tId)
                            }
                            result.fold(
                                onSuccess = { r ->
                                    mergeResultMessage = strings.moveMergeResult(r.itemsMoved, r.conflictsResolved)
                                },
                                onFailure = { e ->
                                    mergeResultMessage = e.message
                                },
                            )
                            viewModel.loadKnowledgeBases()
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

        // Merge result dialog
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
    val strings = koinInject<I18nManager>().strings

    LumeCardDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name, description.ifBlank { null }) },
        confirmText = strings.actionSave,
        confirmEnabled = name.isNotBlank(),
    ) {
        LumeCardTextField(
            value = name,
            onValueChange = { name = it },
            label = strings.fieldName,
        )
        LumeCardTextField(
            value = description,
            onValueChange = { description = it },
            label = strings.fieldDescription,
            singleLine = false,
        )
    }
}
