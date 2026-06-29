package com.lumecard.app.ui.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.AiConfig
import com.lumecard.shared.data.AiConfigManager
import com.lumecard.shared.data.AiProviders
import com.lumecard.shared.data.AiProtocols
import com.lumecard.shared.data.ai.AiCapability
import com.lumecard.shared.data.ai.AiModelListFetcher
import com.lumecard.shared.data.ai.AiProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.koin.compose.koinInject

class AiConfigScreen : Screen {
    override val key: ScreenKey = "AiConfig"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val i18nManager = koinInject<I18nManager>()
        val strings = i18nManager.strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val aiConfigManager: AiConfigManager = koinInject()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var configs by remember { mutableStateOf<List<AiConfig>>(emptyList()) }
        var isEditing by remember { mutableStateOf(false) }
        var editConfig by remember { mutableStateOf<AiConfig?>(null) }

        var editName by remember { mutableStateOf("") }
        var editProvider by remember { mutableStateOf("") }
        var editProtocol by remember { mutableStateOf("") }
        var editBaseUrl by remember { mutableStateOf("") }
        var editApiKey by remember { mutableStateOf("") }
        var editModel by remember { mutableStateOf("") }
        var showApiKey by remember { mutableStateOf(false) }

        var editSystemPrompt by remember { mutableStateOf("") }
        var editTemperature by remember { mutableStateOf("0.7") }
        var editMaxTokens by remember { mutableStateOf("2048") }
        var editTopP by remember { mutableStateOf("1.0") }
        var editFrequencyPenalty by remember { mutableStateOf("0.0") }
        var editPresencePenalty by remember { mutableStateOf("0.0") }

        var testResult by remember { mutableStateOf<String?>(null) }
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }
        var defaultConfig by remember { mutableStateOf<AiConfig?>(null) }
        var editFallbackConfigId by remember { mutableStateOf<String?>(null) }
        var fallbackConfigs by remember { mutableStateOf<List<AiConfig>>(emptyList()) }
        var showModelMenu by remember { mutableStateOf(false) }
        var showFallbackMenu by remember { mutableStateOf(false) }
        var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
        var isFetchingModels by remember { mutableStateOf(false) }
        var rawFetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
        val fetcher: AiModelListFetcher = koinInject()

        fun reloadConfigs() {
            scope.launch {
                try {
                    val all = withContext(Dispatchers.IO) { aiConfigManager.getAll() }
                    configs = all
                    defaultConfig = withContext(Dispatchers.IO) { aiConfigManager.getDefault() }
                } catch (_: Exception) { }
            }
        }

        fun startEdit(config: AiConfig? = null) {
            if (config != null) {
                editConfig = config
                editName = config.name
                editProvider = config.provider
                editProtocol = config.protocol
                editBaseUrl = config.baseUrl
                editApiKey = config.apiKey
                editModel = config.model
                editSystemPrompt = config.systemPrompt
                editTemperature = config.temperature.toString()
                editMaxTokens = config.maxTokens.toString()
                editTopP = config.topP.toString()
                editFrequencyPenalty = config.frequencyPenalty.toString()
                editPresencePenalty = config.presencePenalty.toString()
                editFallbackConfigId = config.fallbackConfigId
            } else {
                val first = AiProviders.all.first()
                editConfig = null
                editName = ""
                editProvider = first.id
                editProtocol = first.defaultProtocol
                editBaseUrl = first.defaultBaseUrl
                editApiKey = ""
                editModel = first.defaultModel
                editSystemPrompt = ""
                editTemperature = "0.7"
                editMaxTokens = "2048"
                editTopP = "1.0"
                editFrequencyPenalty = "0.0"
                editPresencePenalty = "0.0"
                editFallbackConfigId = null
            }
            testResult = null
            isEditing = true
            scope.launch {
                fallbackConfigs = withContext(Dispatchers.IO) { aiConfigManager.getAll() }
                    .filter { it.id != (editConfig?.id ?: "") }
            }
        }

        fun resetEdit() {
            isEditing = false
            editConfig = null
            testResult = null
        }

        LaunchedEffect(Unit) { reloadConfigs() }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.aiTitle,
                    onBack = { navigator.pop() },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.section),
            ) {
                Spacer(modifier = Modifier.height(spacing.xs))

                // Connection Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = radius.pill,
                            color = if (defaultConfig != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        ) {}
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(
                            strings.aiConnectionStatus,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (defaultConfig != null) strings.aiConnected
                            else strings.aiDisconnected,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (defaultConfig != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Section header
                Text(
                    strings.aiConfig,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                )

                // Editing form or config list
                if (isEditing) {
                    // ─── Editing form ──────────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = radius.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            // Config Name
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(strings.aiConfigName) },
                                placeholder = { Text(strings.aiConfigNamePlaceholder) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Provider selector
                            val providerInfoList = AiProviders.all
                            var showProviderMenu by remember { mutableStateOf(false) }
                            val currentProvider = AiProviders.findById(editProvider)
                            val providerDisplay = currentProvider?.displayName ?: editProvider

                            Box {
                                OutlinedTextField(
                                    value = providerDisplay,
                                    onValueChange = {},
                                    label = { Text(strings.aiProvider) },
                                    readOnly = true,
                                    trailingIcon = {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { showProviderMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showProviderMenu,
                                    onDismissRequest = { showProviderMenu = false },
                                ) {
                                    providerInfoList.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.displayName) },
                                            onClick = {
                                                editProvider = p.id
                                                if (editConfig == null || editConfig?.provider != p.id) {
                                                    if (editBaseUrl.isBlank() || editConfig?.provider != p.id) {
                                                        editBaseUrl = p.defaultBaseUrl
                                                    }
                                                    if (editModel.isBlank() || editConfig?.provider != p.id) {
                                                        editModel = p.defaultModel
                                                    }
                                                    editProtocol = p.defaultProtocol
                                                }
                                                if (editName.isBlank()) editName = p.displayName
                                                showProviderMenu = false
                                            },
                                        )
                                    }
                                }
                            }

                            // Protocol selector
                            val protocolList = AiProtocols.all
                            var showProtocolMenu by remember { mutableStateOf(false) }
                            val currentProtocol = AiProtocols.findById(editProtocol)
                            val protocolDisplay = currentProtocol?.displayName ?: editProtocol

                            Box {
                                OutlinedTextField(
                                    value = protocolDisplay,
                                    onValueChange = {},
                                    label = { Text(strings.aiProtocol) },
                                    readOnly = true,
                                    trailingIcon = {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { showProtocolMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showProtocolMenu,
                                    onDismissRequest = { showProtocolMenu = false },
                                ) {
                                    protocolList.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.displayName) },
                                            onClick = {
                                                editProtocol = p.id
                                                showProtocolMenu = false
                                            },
                                        )
                                    }
                                }
                            }

                            // Base URL
                            OutlinedTextField(
                                value = editBaseUrl,
                                onValueChange = { editBaseUrl = it },
                                label = { Text(strings.aiBaseUrl) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // API Key
                            OutlinedTextField(
                                value = editApiKey,
                                onValueChange = { editApiKey = it },
                                label = { Text(strings.aiApiKey) },
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    TextButton(onClick = { showApiKey = !showApiKey }, interactionSource = null) {
                                        Text(
                                            if (showApiKey) "\u2713" else "\u25CB",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Model selector (fetched from API + custom input)
                            val providerSpec = AiProviderRegistry.findById(editProvider)
                            val knownModels = providerSpec?.models ?: emptyList()
                            val modelList = if (fetchedModels != null) rawFetchedModels else emptyList()

                            fun buildEditConfig(): AiConfig = AiConfig(
                                id = editConfig?.id ?: "",
                                name = editName,
                                provider = editProvider,
                                protocol = editProtocol,
                                baseUrl = editBaseUrl,
                                apiKey = editApiKey,
                                model = editModel,
                                systemPrompt = editSystemPrompt,
                                temperature = editTemperature.toFloatOrNull() ?: 0.7f,
                                maxTokens = editMaxTokens.toIntOrNull() ?: 2048,
                                topP = editTopP.toFloatOrNull() ?: 1.0f,
                                frequencyPenalty = editFrequencyPenalty.toFloatOrNull() ?: 0.0f,
                                presencePenalty = editPresencePenalty.toFloatOrNull() ?: 0.0f,
                                fallbackConfigId = editFallbackConfigId,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = editModel,
                                    onValueChange = { editModel = it },
                                    label = { Text(strings.aiModel) },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { showModelMenu = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = strings.aiModel)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(spacing.sm))
                                OutlinedButton(
                                    onClick = {
                                        isFetchingModels = true
                                        scope.launch {
                                            try {
                                                val config = buildEditConfig()
                                                val fetched = withContext(Dispatchers.IO) { fetcher.fetchModels(config) }
                                                rawFetchedModels = fetched
                                                fetchedModels = fetched
                                            } catch (_: Exception) {
                                                if (fetchedModels == null) {
                                                    fetchedModels = emptyList()
                                                    rawFetchedModels = emptyList()
                                                }
                                            } finally {
                                                isFetchingModels = false
                                            }
                                        }
                                    },
                                    enabled = editApiKey.isNotBlank() && editBaseUrl.isNotBlank() && !isFetchingModels,
                                    interactionSource = null,
                                ) {
                                    if (isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.aiFetchModels, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false },
                            ) {
                                modelList.forEach { modelId ->
                                    val modelSpec = knownModels.find { it.id == modelId }
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(modelSpec?.name ?: modelId, style = MaterialTheme.typography.bodyMedium)
                                                    if (modelSpec != null) {
                                                        Text(
                                                            "${modelSpec.contextWindow / 1000}K context",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val config = buildEditConfig()
                                                            withContext(Dispatchers.IO) { fetcher.removeFromCache(config.id, modelId) }
                                                            rawFetchedModels = rawFetchedModels - modelId
                                                            fetchedModels = rawFetchedModels - modelId
                                                        }
                                                    },
                                                    interactionSource = null,
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete model",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            editModel = modelId
                                            showModelMenu = false
                                        },
                                    )
                                }
                                if (modelList.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No models available — type a model name manually") },
                                        onClick = { showModelMenu = false },
                                    )
                                }
                            }

                            // Capability tags
                            val selectedModel = knownModels.find { it.id == editModel }
                            if (selectedModel != null && selectedModel.capabilities.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    selectedModel.capabilities.forEach { cap ->
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                cap.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // Request parameters
                            var showParams by remember { mutableStateOf(false) }
                            TextButton(
                                onClick = { showParams = !showParams },
                                interactionSource = null,
                            ) {
                                Icon(
                                    if (showParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (showParams) "Hide parameters" else "Request parameters")
                            }

                            if (showParams) {
                                // Fallback config selector
                                if (fallbackConfigs.isNotEmpty()) {
                                    val fallbackName = if (editFallbackConfigId != null) {
                                        fallbackConfigs.find { it.id == editFallbackConfigId }?.name ?: strings.aiNotConfigured
                                    } else strings.aiNotConfigured
                                    Box {
                                        OutlinedTextField(
                                            value = fallbackName,
                                            onValueChange = {},
                                            label = { Text("Fallback Config") },
                                            readOnly = true,
                                            trailingIcon = {
                                                IconButton(onClick = { showFallbackMenu = true }) {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        DropdownMenu(
                                            expanded = showFallbackMenu,
                                            onDismissRequest = { showFallbackMenu = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("No fallback") },
                                                onClick = {
                                                    editFallbackConfigId = null
                                                    showFallbackMenu = false
                                                },
                                            )
                                            fallbackConfigs.forEach { fc ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(fc.name)
                                                            Text(
                                                                "${fc.provider} · ${fc.model}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        editFallbackConfigId = fc.id
                                                        showFallbackMenu = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(spacing.sm))
                                }

                                // System Prompt
                                OutlinedTextField(
                                    value = editSystemPrompt,
                                    onValueChange = { editSystemPrompt = it },
                                    label = { Text(strings.aiSystemPrompt) },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                // Temperature (0.0 - 2.0)
                                OutlinedTextField(
                                    value = editTemperature,
                                    onValueChange = { editTemperature = it },
                                    label = { Text(strings.aiTemperature) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                // Context Window
                                val knownModel = providerSpec?.modelById(editModel)
                                OutlinedTextField(
                                    value = editMaxTokens,
                                    onValueChange = { editMaxTokens = it },
                                    label = { Text(strings.aiContextWindow) },
                                    placeholder = { Text(knownModel?.let { "${it.contextWindow / 1000}K" } ?: "") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                // Top P
                                OutlinedTextField(
                                    value = editTopP,
                                    onValueChange = { editTopP = it },
                                    label = { Text(strings.aiTopP) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                // Frequency Penalty
                                OutlinedTextField(
                                    value = editFrequencyPenalty,
                                    onValueChange = { editFrequencyPenalty = it },
                                    label = { Text(strings.aiFrequencyPenalty) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                // Presence Penalty
                                OutlinedTextField(
                                    value = editPresencePenalty,
                                    onValueChange = { editPresencePenalty = it },
                                    label = { Text(strings.aiPresencePenalty) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // Test result
                            if (testResult != null) {
                                val isSuccess = testResult!!.startsWith("OK") || testResult!!.startsWith(strings.aiTestSuccess)
                                Text(
                                    testResult!!,
                                    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            testResult = null
                                            val config = AiConfig(
                                                id = editConfig?.id ?: Clock.System.now().toEpochMilliseconds().toString(),
                                                name = editName.ifBlank { editBaseUrl },
                                                provider = editProvider,
                                                protocol = editProtocol,
                                                baseUrl = editBaseUrl,
                                                apiKey = editApiKey,
                                                model = editModel,
                                                systemPrompt = editSystemPrompt,
                                                temperature = editTemperature.toFloatOrNull() ?: 0.7f,
                                                maxTokens = editMaxTokens.toIntOrNull() ?: 2048,
                                                topP = editTopP.toFloatOrNull() ?: 1.0f,
                                                frequencyPenalty = editFrequencyPenalty.toFloatOrNull() ?: 0.0f,
                                                presencePenalty = editPresencePenalty.toFloatOrNull() ?: 0.0f,
                                                isDefault = editConfig?.isDefault ?: configs.isEmpty(),
                                                fallbackConfigId = editFallbackConfigId,
                                            )
                                            val result = withContext(Dispatchers.IO) { aiConfigManager.testConnection(config) }
                                            testResult = result.fold(
                                                onSuccess = { if (it.startsWith("OK")) strings.aiTestSuccess else it },
                                                onFailure = { strings.aiTestError(it.message ?: "unknown error") },
                                            )
                                            snackbarHostState.showSnackbar(testResult ?: return@launch)
                                        }
                                    },
                                    interactionSource = null,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(strings.aiTestConnection)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val config = AiConfig(
                                                    id = editConfig?.id ?: Clock.System.now().toEpochMilliseconds().toString(),
                                                    name = editName.ifBlank { editBaseUrl },
                                                    provider = editProvider,
                                                    protocol = editProtocol,
                                                    baseUrl = editBaseUrl,
                                                    apiKey = editApiKey,
                                                    model = editModel,
                                                    systemPrompt = editSystemPrompt,
                                                    temperature = editTemperature.toFloatOrNull() ?: 0.7f,
                                                    maxTokens = editMaxTokens.toIntOrNull() ?: 2048,
                                                    topP = editTopP.toFloatOrNull() ?: 1.0f,
                                                    frequencyPenalty = editFrequencyPenalty.toFloatOrNull() ?: 0.0f,
                                                    presencePenalty = editPresencePenalty.toFloatOrNull() ?: 0.0f,
                                                    isDefault = editConfig?.isDefault ?: configs.isEmpty(),
                                                    fallbackConfigId = editFallbackConfigId,
                                                )
                                                withContext(Dispatchers.IO) { aiConfigManager.save(config) }
                                                snackbarHostState.showSnackbar(strings.aiSaveSuccess)
                                                resetEdit()
                                                reloadConfigs()
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(strings.aiTestError(e.message ?: "unknown error"))
                                            }
                                        }
                                    },
                                    interactionSource = null,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(strings.actionSave)
                                }
                            }

                            TextButton(
                                onClick = { resetEdit() },
                                interactionSource = null,
                            ) {
                                Text(strings.actionCancel)
                            }
                        }
                    }
                } else {
                    // ─── Config list ──────────────────────────────────
                    if (configs.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = radius.card,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Text(
                                strings.aiNotConfigured,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(spacing.xl),
                            )
                        }
                    } else {
                        configs.forEach { config ->
                            val isDefault = config.id == defaultConfig?.id
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = radius.card,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Column(modifier = Modifier.padding(spacing.md)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    config.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                if (isDefault) {
                                                    Spacer(Modifier.width(spacing.sm))
                                                    Surface(
                                                        shape = radius.pill,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    ) {
                                                        Text(
                                                            strings.aiDefault,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            val providerName = AiProviders.findById(config.provider)?.displayName ?: config.provider
                                            val protocolName = AiProtocols.findById(config.protocol)?.displayName ?: config.protocol
                                            Text(
                                                "$providerName · $protocolName · ${config.model}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(spacing.sm))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        if (!isDefault) {
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        withContext(Dispatchers.IO) { aiConfigManager.setDefault(config.id) }
                                                        reloadConfigs()
                                                    }
                                                },
                                                interactionSource = null,
                                            ) {
                                                Text(strings.aiSetDefault, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        TextButton(
                                            onClick = { startEdit(config) },
                                            interactionSource = null,
                                        ) {
                                            Text(strings.actionEdit, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(
                                            onClick = { deleteConfirmId = config.id },
                                            interactionSource = null,
                                        ) {
                                            Text(
                                                strings.actionDelete,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(spacing.sm))
                    OutlinedButton(
                        onClick = { startEdit() },
                        interactionSource = null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(spacing.sm))
                        Text(strings.aiAddConfig)
                    }
                }
            }
        }

        // Delete confirm dialog
        deleteConfirmId?.let { id ->
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                title = { Text(strings.aiDeleteConfirm) },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { aiConfigManager.delete(id) }
                                    snackbarHostState.showSnackbar(strings.aiDeleteSuccess)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(strings.aiTestError(e.message ?: "unknown error"))
                                }
                            }
                            deleteConfirmId = null
                            reloadConfigs()
                        },
                        interactionSource = null,
                    ) {
                        Text(strings.actionDelete)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { deleteConfirmId = null },
                        interactionSource = null,
                    ) { Text(strings.actionCancel) }
                },
            )
        }
    }
}
