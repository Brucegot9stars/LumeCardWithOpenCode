package com.lumecard.app.ui.screens.aicard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.platform.copyToClipboard
import com.lumecard.app.platform.pickOpenFile
import com.lumecard.app.platform.readFileContent
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.AiCardMode
import com.lumecard.shared.data.AiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class AiCardScreen : Screen {
    override val key: ScreenKey = "AiCard"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val i18nManager = koinInject<I18nManager>()
        val strings = i18nManager.strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val vm: AiCardViewModel = koinInject()
        val state by vm.state.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var showPromptEditor by remember { mutableStateOf(false) }
        var showLargeCountDialog by remember { mutableStateOf(false) }
        var showRestorePromptDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.aiCardGeneration,
                    onBack = { navigator.pop() },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                // Mode selection
                Text(strings.aiCardGenerationDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                AiCardModeSelector(
                    selectedMode = state.mode,
                    onModeSelected = { vm.setMode(it) },
                    strings = strings,
                )

                // KB/Deck selectors
                when (state.mode) {
                    AiCardMode.SPECIFY_KB, AiCardMode.SPECIFY_BOTH -> {
                        KbSelector(
                            knowledgeBases = state.knowledgeBases,
                            selectedKbId = state.selectedKbId,
                            onKbSelected = { vm.selectKb(it) },
                            strings = strings,
                        )
                    }
                    else -> {}
                }

                if (state.mode == AiCardMode.SPECIFY_BOTH && state.selectedKbId != null) {
                    DeckSelector(
                        decks = state.decks,
                        selectedDeckId = state.selectedDeckId,
                        onDeckSelected = { vm.selectDeck(it) },
                        strings = strings,
                    )
                }

                // Auto-classify decks checkbox (hidden when deck is user-specified)
                if (state.mode != AiCardMode.SPECIFY_BOTH) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = state.autoClassifyDecks,
                            onCheckedChange = { vm.setAutoClassifyDecks(it) },
                        )
                        Spacer(Modifier.width(spacing.sm))
                        Column {
                            Text(strings.aiCardAutoClassify, style = MaterialTheme.typography.bodyMedium)
                            Text(strings.aiCardAutoClassifyDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Config error warning
                if (state.configError) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(spacing.sm))
                            Text(strings.aiCardNoConfigDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                // AI config selector
                if (state.allConfigs.isNotEmpty()) {
                    ConfigSelector(
                        configs = state.allConfigs,
                        selectedConfigId = state.selectedConfigId,
                        onConfigSelected = { vm.selectConfig(it) },
                        strings = strings,
                    )
                }

                // Topic
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.aiCardTopic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (state.topic.isNotEmpty()) {
                        IconButton(onClick = { vm.setTopic("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = strings.aiCardClear, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = state.topic,
                    onValueChange = { vm.setTopic(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(strings.aiCardTopicPlaceholder) },
                    minLines = 2,
                    maxLines = 5,
                )

                // Reference materials
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.aiCardMaterials, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (state.referenceMaterials.isNotEmpty()) {
                        IconButton(onClick = { vm.setReferenceMaterials("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = strings.aiCardClear, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = state.referenceMaterials,
                    onValueChange = { vm.setReferenceMaterials(it) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text(strings.aiCardMaterialsPlaceholder) },
                    maxLines = 15,
                )

                // File import button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val path = withContext(Dispatchers.IO) { pickOpenFile("text/plain") }
                                if (path != null) {
                                    val content = withContext(Dispatchers.IO) { readFileContent(path) }
                                    if (content != null) {
                                        vm.appendReferenceMaterials(content)
                                    } else {
                                        snackbarHostState.showSnackbar(strings.aiCardImportFileError)
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(spacing.sm))
                    Text(strings.aiCardImportFile)
                }
                Text(strings.aiCardSupportedFormats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Card count
                Text(strings.aiCardCount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(strings.aiCardCountDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Slider(
                        value = state.cardCount.toFloat(),
                        onValueChange = { vm.setCardCount(it.toInt()) },
                        valueRange = 1f..50f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    OutlinedTextField(
                        value = state.cardCount.toString(),
                        onValueChange = { s ->
                            val n = s.toIntOrNull() ?: return@OutlinedTextField
                            vm.setCardCount(n.coerceIn(1, 1000))
                        },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                // Generate button
                Button(
                    onClick = {
                        if (state.cardCount > 100) {
                            showLargeCountDialog = true
                        } else {
                            vm.generate()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = state.screenState != AiCardScreenState.GENERATING && !state.configError && state.selectedConfigId != null,
                ) {
                    if (state.screenState == AiCardScreenState.GENERATING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(spacing.sm))
                        val batchProgress = state.batchProgress
                        val downloadProgress = state.downloadProgress
                        val progressText = when {
                            batchProgress != null && batchProgress.totalBatches > 0 ->
                                "${strings.aiCardGenerating} (${batchProgress.savedCards}/${batchProgress.totalTarget})"
                            downloadProgress != null -> {
                                val (received, total) = downloadProgress
                                val receivedStr = formatDataSize(received)
                                if (total != null && total > 0L) {
                                    "${strings.aiCardGenerating} ($receivedStr / ${formatDataSize(total)})"
                                } else {
                                    "${strings.aiCardGenerating} ($receivedStr)"
                                }
                            }
                            else -> strings.aiCardGenerating
                        }
                        Text(progressText, maxLines = 1)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(spacing.sm))
                        Text(strings.aiCardGenerate)
                    }
                }

                // Result display
                val result = state.result
                if (state.screenState == AiCardScreenState.DONE) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(spacing.sm))
                                Text("${strings.aiCardGeneration} - ${strings.actionDone}", fontWeight = FontWeight.Bold)
                            }
                            if (result != null) {
                                Spacer(Modifier.height(spacing.sm))
                                Text(strings.aiCardResultCreated(result.cardsCreated))
                                Spacer(Modifier.height(spacing.sm))
                                Text(
                                    strings.aiCardResultDesc
                                        .replace("{0}", result.knowledgeBaseName)
                                        .replace("{1}", result.deckName)
                                        .replace("{2}", result.cardsCreated.toString()),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(spacing.sm))
                            OutlinedButton(onClick = { vm.resetState() }) {
                                Text(strings.aiCardGenerate)
                            }
                        }
                    }
                }

                // Error display
                val errorMsg = state.errorMessage
                if (state.screenState == AiCardScreenState.ERROR && errorMsg != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(spacing.sm))
                                Text("错误", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(Modifier.height(spacing.sm))
                            Text(
                                errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                softWrap = true,
                            )
                            Spacer(Modifier.height(spacing.sm))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        copyToClipboard(errorMsg, "AI 错误信息")
                                        scope.launch { snackbarHostState.showSnackbar("错误信息已复制") }
                                    } catch (_: Exception) { }
                                },
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制错误信息", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Prompt editor section
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showPromptEditor = !showPromptEditor },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.aiCardPrompt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(
                        if (showPromptEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
                AnimatedVisibility(visible = showPromptEditor) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        OutlinedTextField(
                            value = state.prompt,
                            onValueChange = { vm.setPrompt(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                            maxLines = 20,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        copyToClipboard(state.prompt, strings.aiCardPrompt)
                                        scope.launch { snackbarHostState.showSnackbar(strings.aiCardPromptCopied) }
                                    } catch (_: Exception) { }
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.aiCardPromptCopy, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { showRestorePromptDialog = true }) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.aiCardPromptRestore, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { vm.savePrompt() }) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.actionSave, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.md))
            }
        }

        // Large count confirmation dialog
        if (showLargeCountDialog) {
            AlertDialog(
                onDismissRequest = { showLargeCountDialog = false },
                title = { Text(strings.aiCardConfirmLargeCountTitle) },
                text = { Text(strings.aiCardLargeCountConfirm(state.cardCount)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLargeCountDialog = false
                        vm.generate()
                    }) {
                        Text(strings.actionConfirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLargeCountDialog = false }) {
                        Text(strings.actionCancel)
                    }
                },
            )
        }

        // Restore prompt confirmation dialog
        if (showRestorePromptDialog) {
            AlertDialog(
                onDismissRequest = { showRestorePromptDialog = false },
                title = { Text(strings.aiCardPromptRestore) },
                text = { Text(strings.aiCardPromptRestoreConfirm) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestorePromptDialog = false
                        vm.restoreDefaultPrompt()
                    }) {
                        Text(strings.actionConfirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestorePromptDialog = false }) {
                        Text(strings.actionCancel)
                    }
                },
            )
        }
    }
}

private fun formatDataSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024.0 * 1024.0))} MB"
    }
}

@Composable
private fun AiCardModeSelector(
    selectedMode: AiCardMode,
    onModeSelected: (AiCardMode) -> Unit,
    strings: com.lumecard.app.i18n.I18nStrings,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AiCardModeOption(
            selected = selectedMode == AiCardMode.AUTO,
            onClick = { onModeSelected(AiCardMode.AUTO) },
            title = strings.aiCardModeAuto,
            description = strings.aiCardModeAutoDesc,
        )
        AiCardModeOption(
            selected = selectedMode == AiCardMode.SPECIFY_KB,
            onClick = { onModeSelected(AiCardMode.SPECIFY_KB) },
            title = strings.aiCardModeKb,
            description = strings.aiCardModeKbDesc,
        )
        AiCardModeOption(
            selected = selectedMode == AiCardMode.SPECIFY_BOTH,
            onClick = { onModeSelected(AiCardMode.SPECIFY_BOTH) },
            title = strings.aiCardModeBoth,
            description = strings.aiCardModeBothDesc,
        )
    }
}

@Composable
private fun AiCardModeOption(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    description: String,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KbSelector(
    knowledgeBases: List<com.lumecard.shared.model.KnowledgeBase>,
    selectedKbId: String?,
    onKbSelected: (String) -> Unit,
    strings: com.lumecard.app.i18n.I18nStrings,
) {
    Text(strings.aiCardSelectKb, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (knowledgeBases.isEmpty()) {
        Text(strings.aiCardNoKb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        knowledgeBases.forEach { kb ->
            Card(
                onClick = { onKbSelected(kb.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (kb.id == selectedKbId) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = kb.id == selectedKbId,
                        onClick = { onKbSelected(kb.id) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(kb.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DeckSelector(
    decks: List<com.lumecard.shared.model.Deck>,
    selectedDeckId: String?,
    onDeckSelected: (String) -> Unit,
    strings: com.lumecard.app.i18n.I18nStrings,
) {
    Text(strings.aiCardSelectDeck, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (decks.isEmpty()) {
        Text(strings.aiCardNoDeck, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        decks.forEach { deck ->
            Card(
                onClick = { onDeckSelected(deck.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (deck.id == selectedDeckId) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = deck.id == selectedDeckId,
                        onClick = { onDeckSelected(deck.id) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(deck.name, style = MaterialTheme.typography.bodyMedium)
                        val desc = deck.description
                        if (desc != null) {
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSelector(
    configs: List<AiConfig>,
    selectedConfigId: String?,
    onConfigSelected: (String) -> Unit,
    strings: com.lumecard.app.i18n.I18nStrings,
) {
    val selected = configs.find { it.id == selectedConfigId }
    var showMenu by remember { mutableStateOf(false) }

    Text(strings.aiCardSelectConfigLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Box {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            label = { Text(strings.aiCardSelectConfig) },
            placeholder = { Text(strings.aiCardSelectConfig) },
            readOnly = true,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showMenu = true },
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            configs.forEach { config ->
                DropdownMenuItem(
                    text = { Text(config.name) },
                    onClick = {
                        onConfigSelected(config.id)
                        showMenu = false
                    },
                )
            }
        }
    }
}
