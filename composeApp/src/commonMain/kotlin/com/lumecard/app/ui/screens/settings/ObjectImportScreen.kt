package com.lumecard.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.data.ObjectImportPreview
import com.lumecard.app.data.ObjectLoadOutcome
import com.lumecard.app.data.ObjectPendingImport
import com.lumecard.app.data.ObjectPreviewOutcome
import com.lumecard.app.data.ObjectRunOutcome
import com.lumecard.app.data.ObjectTransferFacade
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.platform.pickOpenFile
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.shared.data.ObjectConflictPolicy
import com.lumecard.shared.data.ObjectDuplicatePolicy
import com.lumecard.shared.data.ObjectImportRequest
import com.lumecard.shared.data.ObjectType
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Independent entry point for scoped imports. The full-import button in Settings remains a
 * separate entry point (it was hardened in this batch): this screen refuses `FULL` files and only
 * handles single-object exports.
 *
 * Flow: pick file -> validate -> resolve name/duplicate/target decisions -> one transactional
 * import -> report. Media rollback and orphan cleanup live in [ObjectTransferFacade].
 */
class ObjectImportScreen : Screen {
    override val key: ScreenKey = "ObjectImport"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val strings = koinInject<I18nManager>().strings
        val facade: ObjectTransferFacade = koinInject()
        val kbRepository: KnowledgeBaseRepository = koinInject()
        val deckRepository: DeckRepository = koinInject()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val knowledgeBases by kbRepository.getAll().collectAsState(initial = emptyList())
        val decks by deckRepository.getAll().collectAsState(initial = emptyList())

        var pending by remember { mutableStateOf<ObjectPendingImport?>(null) }
        var busy by remember { mutableStateOf(false) }
        var conflictPolicy by remember { mutableStateOf(ObjectConflictPolicy.KEEP_BOTH) }
        var duplicatePolicy by remember { mutableStateOf(ObjectDuplicatePolicy.KEEP_BOTH) }
        var createNewKb by remember { mutableStateOf(false) }
        var targetKbId by remember { mutableStateOf<String?>(null) }
        var newKbName by remember { mutableStateOf("") }
        var createNewDeck by remember { mutableStateOf(false) }
        var targetDeckId by remember { mutableStateOf<String?>(null) }
        var newDeckName by remember { mutableStateOf("") }
        var resultMessage by remember { mutableStateOf<String?>(null) }
        var confirmPreview by remember { mutableStateOf<ObjectImportPreview?>(null) }
        var confirmRequest by remember { mutableStateOf<ObjectImportRequest?>(null) }

        /**
         * Run the import and return a message the caller must show *after* clearing `busy`.
         * Showing it here would suspend this coroutine inside `try` for the snackbar's lifetime
         * and leave the button spinning for seconds after the import had already finished.
         */
        suspend fun performImport(current: ObjectPendingImport, request: ObjectImportRequest): String? =
            when (val outcome = facade.import(current, request)) {
                is ObjectRunOutcome.Success -> {
                    resultMessage = outcome.message
                    pending = null
                    null
                }
                is ObjectRunOutcome.Committed -> {
                    // The data landed; keep the message but clear the file so a retry cannot
                    // import everything twice.
                    resultMessage = outcome.message
                    pending = null
                    null
                }
                is ObjectRunOutcome.Failure -> outcome.message
            }

        fun loadFile() {
            // Set the flag before launching: a double tap can land before recomposition
            // disables the button, and two imports share one media root.
            if (busy) return
            busy = true
            scope.launch {
                var message: String? = null
                try {
                    val path = pickOpenFile("application/zip")
                    if (path == null) {
                        message = strings.settingsImportHint
                        return@launch
                    }
                    when (val outcome = facade.load(path)) {
                        is ObjectLoadOutcome.Success -> {
                            pending = outcome.pending
                            conflictPolicy = ObjectConflictPolicy.KEEP_BOTH
                            duplicatePolicy = ObjectDuplicatePolicy.KEEP_BOTH
                            createNewKb = knowledgeBases.isEmpty()
                            // No pre-selected target: importing into "whichever deck was created
                            // first" is a silent mistake the user cannot see or undo easily.
                            targetKbId = null
                            createNewDeck = decks.isEmpty()
                            targetDeckId = null
                            newKbName = ""
                            newDeckName = ""
                        }
                        is ObjectLoadOutcome.Failure -> {
                            // Drop the previously loaded file. Keeping it would leave the wizard
                            // showing one object while a different file sits behind "start",
                            // which is exactly how the wrong data gets imported.
                            pending = null
                            message = outcome.message
                        }
                    }
                } finally {
                    busy = false
                    message?.let { snackbarHostState.showSnackbar(it) }
                }
            }
        }

        val p = pending
        val needKb = p != null &&
            (p.type == ObjectType.DECK || (p.type == ObjectType.CARD && createNewDeck))
        val needDeck = p?.type == ObjectType.CARD
        val canStart = when {
            busy || p == null -> false
            // Validate the id still exists: the target may have been deleted on another screen
            // while this wizard was open, and "target not found" after the click is unhelpful.
            p.type == ObjectType.DECK && !createNewKb && knowledgeBases.none { it.id == targetKbId } -> false
            p.type == ObjectType.DECK && createNewKb && newKbName.isBlank() -> false
            p.type == ObjectType.CARD && !createNewDeck && decks.none { it.id == targetDeckId } -> false
            p.type == ObjectType.CARD && createNewDeck && newDeckName.isBlank() -> false
            p.type == ObjectType.CARD && createNewDeck && !createNewKb &&
                knowledgeBases.none { it.id == targetKbId } -> false
            p.type == ObjectType.CARD && createNewDeck && createNewKb && newKbName.isBlank() -> false
            else -> true
        }

        Scaffold(
            topBar = {
                // Leaving while the import is running cancels the composition's scope. The
                // facade protects the database commit and the media cleanup with NonCancellable,
                // so files and rows stay consistent; only the result message can be lost.
                // System back and tab switches still bypass this guard (no common BackHandler),
                // which remains a known limitation of this screen.
                LumeCardTopBar(
                    title = strings.objectTransferTitle,
                    onBack = { if (!busy) navigator.pop() },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Shared by both branches: re-picking a file also blocks the form, and without a
                // progress bar that just looks frozen.
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (p == null) {
                    Text(strings.objectTransferEntryDesc, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { loadFile() }, enabled = !busy) {
                        Text(strings.objectTransferPickFile)
                    }
                } else {
                    FileSummaryCard(
                        typeLabel = strings.objectTransferTypeLabel(p.type.wire),
                        objectName = p.objectName,
                        typeCaption = strings.objectTransferFileType,
                        objectCaption = strings.objectTransferObjectName,
                        repickLabel = strings.objectTransferRepick,
                        onRepick = { loadFile() },
                        busy = busy,
                    )

                    // Only the choices that can actually change the outcome are shown: a card
                    // import has no knowledge-base name to resolve unless it also creates a deck.
                    if (p.type != ObjectType.CARD || createNewDeck) {
                        SectionTitle(strings.objectTransferConflictTitle)
                        ChoiceRow(
                            label = strings.objectTransferConflictKeepBoth,
                            selected = conflictPolicy == ObjectConflictPolicy.KEEP_BOTH,
                            enabled = !busy,
                            onSelect = { conflictPolicy = ObjectConflictPolicy.KEEP_BOTH },
                        )
                        ChoiceRow(
                            label = strings.objectTransferConflictOverwrite,
                            selected = conflictPolicy == ObjectConflictPolicy.OVERWRITE,
                            enabled = !busy,
                            onSelect = { conflictPolicy = ObjectConflictPolicy.OVERWRITE },
                        )
                    }

                    if (needKb) {
                        SectionTitle(strings.objectTransferTargetKb)
                        if (knowledgeBases.isEmpty()) {
                            Text(
                                strings.objectTransferNoKb,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ChoiceRow(
                                label = strings.objectTransferTargetKb,
                                selected = !createNewKb,
                                enabled = !busy,
                                onSelect = { createNewKb = false },
                            )
                            if (!createNewKb) {
                                TargetPicker(
                                    currentName = knowledgeBases.firstOrNull { it.id == targetKbId }?.name
                                        ?: strings.objectTransferTargetKb,
                                    names = knowledgeBases.map { it.id to it.name },
                                    enabled = !busy,
                                    onPick = { targetKbId = it },
                                )
                            }
                        }
                        ChoiceRow(
                            label = strings.objectTransferCreateKb,
                            selected = createNewKb,
                            enabled = !busy,
                            onSelect = { createNewKb = true },
                        )
                        if (createNewKb) {
                            OutlinedTextField(
                                value = newKbName,
                                onValueChange = { newKbName = it },
                                label = { Text(strings.objectTransferNewKbName) },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (needDeck) {
                        SectionTitle(strings.objectTransferTargetDeck)
                        if (decks.isNotEmpty()) {
                            ChoiceRow(
                                label = strings.objectTransferTargetDeck,
                                selected = !createNewDeck,
                                enabled = !busy,
                                onSelect = { createNewDeck = false },
                            )
                            if (!createNewDeck) {
                                TargetPicker(
                                    currentName = decks.firstOrNull { it.id == targetDeckId }?.name
                                        ?: strings.objectTransferTargetDeck,
                                    // Two decks can share a name across knowledge bases; show the
                                    // parent so the user knows which one they are picking.
                                    names = decks.map { d ->
                                        val kbName = knowledgeBases.firstOrNull { it.id == d.knowledgeBaseId }?.name
                                        d.id to if (kbName.isNullOrBlank()) d.name else "$kbName / ${d.name}"
                                    },
                                    enabled = !busy,
                                    onPick = { targetDeckId = it },
                                )
                            }
                        }
                        ChoiceRow(
                            label = strings.objectTransferCreateDeck,
                            selected = createNewDeck,
                            enabled = !busy,
                            onSelect = { createNewDeck = true },
                        )
                        if (createNewDeck) {
                            OutlinedTextField(
                                value = newDeckName,
                                onValueChange = { newDeckName = it },
                                label = { Text(strings.objectTransferNewDeckName) },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (needDeck) {
                        SectionTitle(strings.objectTransferDuplicateTitle)
                        ChoiceRow(
                            label = strings.objectTransferDuplicateKeepBoth,
                            selected = duplicatePolicy == ObjectDuplicatePolicy.KEEP_BOTH,
                            enabled = !busy,
                            onSelect = { duplicatePolicy = ObjectDuplicatePolicy.KEEP_BOTH },
                        )
                        ChoiceRow(
                            label = strings.objectTransferDuplicateOverwrite,
                            selected = duplicatePolicy == ObjectDuplicatePolicy.OVERWRITE,
                            enabled = !busy,
                            onSelect = { duplicatePolicy = ObjectDuplicatePolicy.OVERWRITE },
                        )
                        ChoiceRow(
                            label = strings.objectTransferDuplicateSkip,
                            selected = duplicatePolicy == ObjectDuplicatePolicy.SKIP,
                            enabled = !busy,
                            onSelect = { duplicatePolicy = ObjectDuplicatePolicy.SKIP },
                        )
                    }

                    Button(
                        onClick = {
                            val current = p
                            val request = ObjectImportRequest(
                                conflictPolicy = conflictPolicy,
                                duplicatePolicy = duplicatePolicy,
                                targetKnowledgeBaseId = if (needKb && !createNewKb) targetKbId else null,
                                newKnowledgeBaseName = if (needKb && createNewKb) {
                                    newKbName.trim().takeIf { it.isNotEmpty() }
                                } else null,
                                targetDeckId = if (needDeck && !createNewDeck) targetDeckId else null,
                                newDeckName = if (needDeck && createNewDeck) {
                                    newDeckName.trim().takeIf { it.isNotEmpty() }
                                } else null,
                            )
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    var message: String? = null
                                    try {
                                        // Preview first: an overwrite must state its delete
                                        // counts before the user commits to it.
                                        message = when (val preview = facade.preview(current, request)) {
                                            is ObjectPreviewOutcome.Success ->
                                                if (preview.preview.isDestructive) {
                                                    confirmPreview = preview.preview
                                                    confirmRequest = request
                                                    null
                                                } else {
                                                    performImport(current, request)
                                                }
                                            is ObjectPreviewOutcome.Failure -> preview.message
                                        }
                                    } finally {
                                        busy = false
                                        message?.let { snackbarHostState.showSnackbar(it) }
                                    }
                                }
                            }
                        },
                        enabled = canStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (busy) strings.objectTransferBusy else strings.objectTransferStart)
                    }
                }
            }
        }

        if (resultMessage != null) {
            AlertDialog(
                onDismissRequest = { resultMessage = null },
                title = { Text(strings.objectTransferResultTitle) },
                text = { Text(resultMessage ?: "") },
                confirmButton = {
                    TextButton(onClick = { resultMessage = null }) { Text(strings.actionOk) }
                },
            )
        }

        confirmPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = {
                    confirmPreview = null
                    confirmRequest = null
                },
                title = { Text(strings.objectTransferConfirmTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Name the file being imported: the counts alone do not tell the user
                        // which object they are about to replace.
                        pending?.let {
                            Text(
                                "${strings.objectTransferObjectName}: ${it.objectName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            strings.objectTransferPlanWillDelete(
                                preview.deletedKnowledgeBases,
                                preview.deletedDecks,
                                preview.deletedCards,
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val request = confirmRequest
                            val current = pending
                            confirmPreview = null
                            confirmRequest = null
                            if (request != null && current != null && !busy) {
                                busy = true
                                scope.launch {
                                    var message: String? = null
                                    try {
                                        message = performImport(current, request)
                                    } finally {
                                        busy = false
                                        message?.let { snackbarHostState.showSnackbar(it) }
                                    }
                                }
                            }
                        },
                    ) { Text(strings.objectTransferConfirmOverwrite) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            confirmPreview = null
                            confirmRequest = null
                        },
                    ) { Text(strings.actionCancel) }
                },
            )
        }
    }
}

@Composable
private fun FileSummaryCard(
    typeLabel: String,
    objectName: String,
    typeCaption: String,
    objectCaption: String,
    repickLabel: String,
    onRepick: () -> Unit,
    busy: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$typeCaption: $typeLabel", style = MaterialTheme.typography.bodyMedium)
            Text("$objectCaption: $objectName", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRepick, enabled = !busy) { Text(repickLabel) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // `selectable` gives screen readers the radio role and the checked state; a bare
            // clickable row reads as plain text.
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TargetPicker(
    currentName: String,
    names: List<Pair<String, String>>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(currentName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { expanded = false; onPick(id) },
                )
            }
        }
    }
}
