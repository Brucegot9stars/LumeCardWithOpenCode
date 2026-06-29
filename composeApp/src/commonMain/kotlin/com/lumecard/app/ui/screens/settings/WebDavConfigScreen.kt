package com.lumecard.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.platform.MediaFileEntry
import com.lumecard.app.platform.hashFileSha1
import com.lumecard.app.platform.scanMediaDirectory
import com.lumecard.app.platform.scanMediaDirectoryRaw
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.data.MediaManager
import com.lumecard.shared.data.SyncHistoryEntry
import com.lumecard.shared.data.MediaManifest
import com.lumecard.shared.data.MediaManifestEntry
import com.lumecard.shared.data.SyncManager
import com.lumecard.shared.data.WebDavConfig
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.shared.data.WebDavProviders
import com.lumecard.shared.data.toCard
import com.lumecard.shared.data.toDeck
import com.lumecard.shared.data.toKnowledgeBase
import com.lumecard.shared.data.toLearningPlan
import com.lumecard.shared.data.toReviewLog
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import com.lumecard.shared.repository.LearningPlanRepository
import com.lumecard.shared.repository.ReviewLogRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import org.koin.compose.koinInject

class WebDavConfigScreen : Screen {
    override val key: ScreenKey = "WebDavConfig"

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("OverloadResolutionAmbiguity")
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val i18nManager = koinInject<I18nManager>()
        val strings = i18nManager.strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val webDavConfigManager: WebDavConfigManager = koinInject()
        val syncManager: SyncManager = koinInject()
        val exportManager: ExportManager = koinInject()
        val mediaManager: MediaManager = koinInject()
        val deckRepository: DeckRepository = koinInject()
        val cardRepository: CardRepository = koinInject()
        val knowledgeBaseRepository: KnowledgeBaseRepository = koinInject()
        val reviewLogRepository: ReviewLogRepository = koinInject()
        val planRepository: LearningPlanRepository = koinInject()
        val settingsRepository: SettingsRepository = koinInject()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var configs by remember { mutableStateOf<List<WebDavConfig>>(emptyList()) }
        var isEditing by remember { mutableStateOf(false) }
        var editConfig by remember { mutableStateOf<WebDavConfig?>(null) }
        var editName by remember { mutableStateOf("") }
        var editUrl by remember { mutableStateOf("") }
        var editUser by remember { mutableStateOf("") }
        var editPass by remember { mutableStateOf("") }
        var showPass by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<String?>(null) }
        var isSyncing by remember { mutableStateOf(false) }
        var syncStatus by remember { mutableStateOf("") }
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }
        var showRestoreConfirm by remember { mutableStateOf(false) }
        var showRestoreHistory by remember { mutableStateOf(false) }
        var historyEntries by remember { mutableStateOf<List<SyncHistoryEntry>>(emptyList()) }
        var restoreHistoryTarget by remember { mutableStateOf<SyncHistoryEntry?>(null) }
        var autoSyncEnabled by remember { mutableStateOf(false) }
        var autoSyncInterval by remember { mutableStateOf(30) }
        var showIntervalDropdown by remember { mutableStateOf(false) }
        var defaultConfig by remember { mutableStateOf<WebDavConfig?>(null) }

        val providerPresets = WebDavProviders.all.map { provider ->
            Triple(provider.name, provider.url, provider.id)
        }

        fun reloadConfigs() {
            scope.launch {
                try {
                    val allConfigs = withContext(Dispatchers.IO) { webDavConfigManager.getAll() }
                    configs = allConfigs
                    defaultConfig = withContext(Dispatchers.IO) { webDavConfigManager.getDefault() }
                } catch (_: Exception) { }
            }
        }

        fun loadAutoSyncSettings() {
            scope.launch {
                autoSyncEnabled = settingsRepository.getBoolean("autoSyncEnabled", false)
                autoSyncInterval = settingsRepository.getInt("autoSyncInterval", 30)
            }
        }

        fun saveAutoSyncSettings() {
            scope.launch {
                settingsRepository.set("autoSyncEnabled", autoSyncEnabled.toString())
                settingsRepository.set("autoSyncInterval", autoSyncInterval.toString())
            }
        }

        LaunchedEffect(Unit) {
            reloadConfigs()
            loadAutoSyncSettings()
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.settingsCloudSync,
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
                            strings.settingsConnectionStatus,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (defaultConfig != null) strings.settingsConnected
                            else strings.settingsDisconnected,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (defaultConfig != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Section header: Config
                Text(
                    strings.settingsSyncDialogTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                )

                // Config list or editing form
                if (isEditing) {
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
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(strings.settingsSyncConfigName) },
                                placeholder = { Text(strings.settingsSyncConfigNamePlaceholder) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            var showProviderMenu by remember { mutableStateOf(false) }
                            var selectedProviderId by remember { mutableStateOf<String?>(null) }
                            val detectedProvider = remember(editUrl) {
                                if (editUrl.isNotBlank()) WebDavProviders.detectProvider(editUrl) else null
                            }
                            val displayProviderName = remember(selectedProviderId, editUrl, detectedProvider) {
                                selectedProviderId?.let { id ->
                                    providerPresets.firstOrNull { it.third == id }?.first
                                } ?: detectedProvider?.name
                                    ?: providerPresets.firstOrNull { it.second == editUrl }?.first
                                    ?: strings.webdavProviderCustom
                            }
                            Box {
                                OutlinedTextField(
                                    value = displayProviderName,
                                    onValueChange = {},
                                    label = { Text(strings.webdavProviderLabel) },
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
                                    providerPresets.forEach { (name, url, id) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                selectedProviderId = id
                                                editUrl = url
                                                if (editName.isBlank()) editName = name
                                                showProviderMenu = false
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(strings.webdavProviderCustom) },
                                        onClick = {
                                            selectedProviderId = null
                                            showProviderMenu = false
                                        },
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                label = { Text(strings.settingsWebdavUrl) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = editUser,
                                onValueChange = { editUser = it },
                                label = { Text(strings.settingsWebdavUser) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = editPass,
                                onValueChange = { editPass = it },
                                label = { Text(strings.settingsWebdavPass) },
                                singleLine = true,
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    TextButton(onClick = { showPass = !showPass }, interactionSource = null) {
                                        Text(
                                            if (showPass) "\u2713" else "\u25CB",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (testResult != null) {
                                val isSuccess = testResult!!.startsWith("HTTP") || testResult!!.startsWith(strings.settingsSyncTestSuccess)
                                Text(
                                    testResult!!,
                                    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            testResult = null
                                            val config = WebDavConfig(
                                                id = editConfig?.id ?: Clock.System.now().toEpochMilliseconds().toString(),
                                                name = editName.ifBlank { editUrl },
                                                url = editUrl,
                                                username = editUser,
                                                password = editPass,
                                                isDefault = editConfig?.isDefault ?: configs.isEmpty(),
                                            )
                                            val result = withContext(Dispatchers.IO) { webDavConfigManager.testConnection(config) }
                                            testResult = result.fold(
                                                onSuccess = { strings.settingsSyncTestSuccess },
                                                onFailure = { strings.settingsSyncTestError(it.message ?: strings.errorUnknown) }
                                            )
                                            val msg = testResult ?: return@launch
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    },
                                    interactionSource = null,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(strings.settingsSyncTestConnection)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val config = WebDavConfig(
                                                    id = editConfig?.id ?: Clock.System.now().toEpochMilliseconds().toString(),
                                                    name = editName.ifBlank { editUrl },
                                                    url = editUrl,
                                                    username = editUser,
                                                    password = editPass,
                                                    isDefault = editConfig?.isDefault ?: configs.isEmpty(),
                                                )
                                                withContext(Dispatchers.IO) { webDavConfigManager.save(config) }
                                                isEditing = false
                                                editConfig = null
                                                testResult = null
                                                reloadConfigs()
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: strings.errorUnknown))
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
                                onClick = {
                                    isEditing = false
                                    editConfig = null
                                    testResult = null
                                },
                                interactionSource = null,
                            ) {
                                Text(strings.actionCancel)
                            }
                        }
                    }
                } else {
                    if (configs.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = radius.card,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Text(
                                strings.settingsSyncNotConfigured,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(spacing.xl),
                            )
                        }
                    } else {
                        configs.forEach { config ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = radius.card,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Column(modifier = Modifier.padding(spacing.md)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            config.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (config.isDefault) {
                                            Surface(
                                                shape = radius.pill,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                            ) {
                                                Text(
                                                    strings.settingsSyncDefault,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        config.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val lastSync = config.lastSyncAt
                                    Text(
                                        if (lastSync != null) "${strings.settingsLastSyncTime}: ${lastSync.take(10)}"
                                        else strings.settingsSyncNever,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(spacing.sm))
                                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                        FilledTonalButton(
                                            onClick = {
                                                editConfig = config
                                                editName = config.name
                                                editUrl = config.url
                                                editUser = config.username
                                                editPass = config.password
                                                testResult = null
                                                isEditing = true
                                            },
                                            modifier = Modifier.height(32.dp),
                                        ) {
                                            Text(strings.actionEdit, style = MaterialTheme.typography.labelMedium)
                                        }
                                        FilledTonalButton(
                                            onClick = { deleteConfirmId = config.id },
                                            modifier = Modifier.height(32.dp),
                                        ) {
                                            Text(strings.actionDelete, style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (!config.isDefault) {
                                            FilledTonalButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            withContext(Dispatchers.IO) { webDavConfigManager.setDefault(config.id) }
                                                            reloadConfigs()
                                                        } catch (e: Exception) {
                                                            snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: strings.errorUnknown))
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp),
                                            ) {
                                                Text(strings.settingsSyncSetDefault, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            editConfig = null
                            editName = ""
                            editUrl = ""
                            editUser = ""
                            editPass = ""
                            testResult = null
                            isEditing = true
                        },
                        interactionSource = null,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(strings.settingsSyncAddConfig)
                    }
                }

                // Auto Sync
                if (!isEditing) {
                    Text(
                        strings.settingsAutoSync,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = radius.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.md, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    strings.settingsAutoSyncDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = autoSyncEnabled,
                                    onCheckedChange = {
                                        autoSyncEnabled = it
                                        saveAutoSyncSettings()
                                    },
                                )
                            }
                            // TODO: 实现定时自动同步调度逻辑
                            //   - 用 LaunchedEffect + delay(autoSyncInterval * 60_000) 循环调用 syncIncrementalData
                            //   - 在 autoSyncEnabled / autoSyncInterval 变化时重启协程
                            //   - 需要持有 config / repositories 等依赖
                            if (autoSyncEnabled) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.md))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showIntervalDropdown = true }
                                        .padding(horizontal = spacing.md, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        strings.settingsAutoSyncInterval,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box {
                                        Text(
                                            "${autoSyncInterval}m",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        DropdownMenu(
                                            expanded = showIntervalDropdown,
                                            onDismissRequest = { showIntervalDropdown = false },
                                        ) {
                                            listOf(15, 30, 60, 120).forEach { interval ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(when (interval) {
                                                            15 -> strings.settingsAutoSyncMin15
                                                            30 -> strings.settingsAutoSyncMin30
                                                            60 -> strings.settingsAutoSyncMin60
                                                            else -> strings.settingsAutoSyncMin120
                                                        })
                                                    },
                                                    onClick = {
                                                        autoSyncInterval = interval
                                                        showIntervalDropdown = false
                                                        saveAutoSyncSettings()
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

                // Action buttons
                if (!isEditing && defaultConfig != null) {
                    Text(
                        strings.actionConfigure,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    )

                    if (isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (syncStatus.isNotEmpty()) {
                        Text(
                            syncStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Sync Now (data + config)
                    Button(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                syncStatus = strings.settingsSyncing
                                try {
                                    val config = defaultConfig ?: return@launch
                                    val deckCount: Int
                                    withContext(Dispatchers.IO) {
                                        deckCount = syncIncrementalData(config, knowledgeBaseRepository, deckRepository, cardRepository, reviewLogRepository, planRepository, exportManager, syncManager, mediaManager)
                                        val settings = settingsRepository.getAll()
                                        val configJson = exportManager.exportConfig(settings)
                                        syncManager.uploadConfig(config, configJson).getOrThrow()
                                        webDavConfigManager.updateLastSync(config.id)
                                    }
                                    syncStatus = strings.settingsSyncSuccess(deckCount)
                                    snackbarHostState.showSnackbar(strings.settingsSyncSuccess(deckCount))
                                    reloadConfigs()
                                } catch (e: Exception) {
                                    syncStatus = strings.settingsSyncError(e.message ?: strings.errorUnknown)
                                    snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: strings.errorUnknown))
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        interactionSource = null,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(spacing.sm))
                        Text(strings.settingsSyncNow)
                    }

                    // Sync Data / Sync Config / Sync Media
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    syncStatus = strings.settingsSyncing
                                    try {
                                        val config = defaultConfig ?: return@launch
                                        val deckCount: Int
                                        withContext(Dispatchers.IO) {
                                            deckCount = syncIncrementalData(config, knowledgeBaseRepository, deckRepository, cardRepository, reviewLogRepository, planRepository, exportManager, syncManager, mediaManager)
                                            webDavConfigManager.updateLastSync(config.id)
                                        }
                                        syncStatus = strings.settingsSyncSuccess(deckCount)
                                        snackbarHostState.showSnackbar(strings.settingsSyncSuccess(deckCount))
                                    } catch (e: Exception) {
                                        syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                        snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            interactionSource = null,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                        ) {
                            Text(strings.settingsSyncData)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    syncStatus = strings.settingsSyncing
                                    try {
                                        val config = defaultConfig ?: return@launch
                                        withContext(Dispatchers.IO) {
                                            val settings = settingsRepository.getAll()
                                            val configJson = exportManager.exportConfig(settings)
                                            syncManager.uploadConfig(config, configJson).getOrThrow()
                                            webDavConfigManager.updateLastSync(config.id)
                                        }
                                        syncStatus = strings.settingsSyncConfigSuccess
                                        snackbarHostState.showSnackbar(strings.settingsSyncConfigSuccess)
                                    } catch (e: Exception) {
                                        syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                        snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            interactionSource = null,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                        ) {
                            Text(strings.settingsSyncConfig)
                        }
                    }

                    // Restore from cloud
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        interactionSource = null,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(strings.settingsRestoreFromCloud)
                    }

                    // Restore history
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val config = defaultConfig ?: return@launch
                                    val index = withContext(Dispatchers.IO) {
                                        syncManager.downloadHistoryIndex(config)
                                    }
                                    if (index.isSuccess) {
                                        historyEntries = index.getOrThrow().entries.reversed()
                                        showRestoreHistory = true
                                    } else {
                                        snackbarHostState.showSnackbar("No history found")
                                    }
                                } catch (_: Exception) {
                                    snackbarHostState.showSnackbar("Failed to load history")
                                }
                            }
                        },
                        interactionSource = null,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(strings.syncRestoreHistory)
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xxl))
            }
        }

        // Delete confirm dialog
        if (deleteConfirmId != null) {
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                title = { Text(strings.settingsSyncDeleteConfirm) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val id = deleteConfirmId ?: return@launch
                                withContext(Dispatchers.IO) { webDavConfigManager.delete(id) }
                                deleteConfirmId = null
                                reloadConfigs()
                            } catch (e: Exception) {
                                deleteConfirmId = null
                                snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: strings.errorUnknown))
                            }
                        }
                    }, interactionSource = null) { Text(strings.actionConfirm) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmId = null }, interactionSource = null) { Text(strings.actionCancel) }
                },
            )
        }

        // Restore history dialog
        if (showRestoreHistory) {
            AlertDialog(
                onDismissRequest = { showRestoreHistory = false },
                title = { Text(strings.syncRestoreHistory) },
                text = {
                    if (historyEntries.isEmpty()) {
                        Text(strings.syncNoHistoryAvailable)
                    } else {
                        Column {
                            historyEntries.forEach { entry ->
                                TextButton(
                                    onClick = {
                                        restoreHistoryTarget = entry
                                        showRestoreHistory = false
                                        scope.launch {
                                            isSyncing = true
                                            syncStatus = strings.settingsSyncing
                                            try {
                                                val config = defaultConfig ?: return@launch
                                                val result = withContext(Dispatchers.IO) {
                                                    val remoteResult = syncManager.downloadSnapshot(config, entry.filename)
                                                    var restoredDecks = 0
                                                    if (remoteResult.isSuccess) {
                                                        val remote = exportManager.importData(remoteResult.getOrThrow())
                                                        if (remote != null) {
                                                            for (kb in knowledgeBaseRepository.getAll().first()) {
                                                                knowledgeBaseRepository.delete(kb.id)
                                                            }
                                                            for (deck in deckRepository.getAll().first()) {
                                                                deckRepository.delete(deck.id)
                                                            }
                                                            for (card in cardRepository.getAll().first()) {
                                                                cardRepository.delete(card.id)
                                                            }
                                                            for (kb in remote.knowledgeBases) {
                                                                knowledgeBaseRepository.insert(kb.toKnowledgeBase())
                                                            }
                                                            for (deck in remote.decks) {
                                                                deckRepository.insert(deck.toDeck())
                                                                restoredDecks++
                                                            }
                                                            for (card in remote.cards) {
                                                                cardRepository.insert(card.toCard())
                                                            }
                                                            for (log in remote.reviewLogs) {
                                                                reviewLogRepository.insert(log.toReviewLog())
                                                            }
                                                            for (plan in remote.learningPlans) {
                                                                planRepository.insert(plan.toLearningPlan())
                                                    }
                                                }
                                            }
                                            restoreSettingsAndFonts(config, syncManager, settingsRepository)
                                            webDavConfigManager.updateLastSync(config.id)
                                            restoredDecks
                                        }
                                        syncStatus = strings.settingsSyncSuccess(result)
                                        snackbarHostState.showSnackbar(strings.settingsSyncSuccess(result))
                                                reloadConfigs()
                                            } catch (e: Exception) {
                                                syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                                snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: strings.errorUnknown))
                                            } finally {
                                                isSyncing = false
                                            }
                                        }
                                    },
                                    interactionSource = null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        strings.syncHistoryEntryFormat(entry.timestamp, entry.deviceId),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRestoreHistory = false }, interactionSource = null) {
                        Text(strings.actionCancel)
                    }
                },
            )
        }

        // Restore confirm dialog
        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text(strings.settingsRestoreConfirm) },
                text = { Text(strings.settingsRestoreConfirmDesc) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirm = false
                        scope.launch {
                            isSyncing = true
                            syncStatus = strings.settingsSyncing
                            try {
                                val config = defaultConfig ?: return@launch
                                val result = withContext(Dispatchers.IO) {
                                    val remoteResult = syncManager.download(config)
                                    var restoredDecks = 0
                                    if (remoteResult.isSuccess) {
                                        val remote = exportManager.importData(remoteResult.getOrThrow())
                                        if (remote != null) {
                                            for (kb in knowledgeBaseRepository.getAll().first()) {
                                                knowledgeBaseRepository.delete(kb.id)
                                            }
                                            for (deck in deckRepository.getAll().first()) {
                                                deckRepository.delete(deck.id)
                                            }
                                            for (card in cardRepository.getAll().first()) {
                                                cardRepository.delete(card.id)
                                            }
                                            for (kb in remote.knowledgeBases) {
                                                knowledgeBaseRepository.insert(kb.toKnowledgeBase())
                                            }
                                            for (deck in remote.decks) {
                                                deckRepository.insert(deck.toDeck())
                                                restoredDecks++
                                            }
                                            for (card in remote.cards) {
                                                cardRepository.insert(card.toCard())
                                            }
                                            for (log in remote.reviewLogs) {
                                                reviewLogRepository.insert(log.toReviewLog())
                                            }
                                            for (plan in remote.learningPlans) {
                                                planRepository.insert(plan.toLearningPlan())
                                            }
                                        }
                                    }
                                    restoreSettingsAndFonts(config, syncManager, settingsRepository)
                                    webDavConfigManager.updateLastSync(config.id)
                                    restoredDecks
                                }
                                syncStatus = strings.settingsSyncSuccess(result)
                                snackbarHostState.showSnackbar(strings.settingsSyncSuccess(result))
                                reloadConfigs()
                            } catch (e: Exception) {
                                syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                            } finally {
                                isSyncing = false
                            }
                        }
                    }, interactionSource = null) { Text(strings.actionConfirm) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }, interactionSource = null) { Text(strings.actionCancel) }
                },
            )
        }
    }
}

private val fontManifestJson = Json { ignoreUnknownKeys = true }

private suspend fun syncIncrementalData(
    config: WebDavConfig,
    kbRepository: KnowledgeBaseRepository,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    reviewLogRepository: ReviewLogRepository,
    planRepository: LearningPlanRepository,
    exportManager: ExportManager,
    syncManager: SyncManager,
    mediaManager: MediaManager,
): Int {
    val now = Clock.System.now()

    syncManager.archiveCurrentSnapshot(config)

    val allKbs = kbRepository.getAll().first()
    val allDecks = deckRepository.getAll().first()
    val allCards = cardRepository.getAll().first()
    val allLogs = reviewLogRepository.getAll().first()
    val allPlans = planRepository.getAll().first()

    val json = exportManager.exportData(allKbs, allDecks, allCards, allLogs, allPlans)
    syncManager.uploadData(config, json).getOrThrow()

    kbRepository.markSynced(allKbs.map { it.id }, now)
    deckRepository.markSynced(allDecks.map { it.id }, now)
    cardRepository.markSynced(allCards.map { it.id }, now)
    reviewLogRepository.markSynced(allLogs.map { it.id }, now)
    planRepository.markSynced(allPlans.map { it.id }, now)

    // Anki-style media sync: cache mtime + SHA-1 to avoid re-hashing unchanged files
    val mediaBase = System.getProperty("lumecard.media.dir") ?: "${System.getProperty("user.home")}/.lumecard/media"
    val rawFiles = scanMediaDirectoryRaw(mediaBase)
    if (rawFiles.isNotEmpty()) {
        val resolved = rawFiles.map { raw ->
            val cachedHash = mediaManager.getCachedHash(raw.relativePath, raw.mtime)
            if (cachedHash != null) {
                MediaManifestEntry(raw.relativePath, raw.size, cachedHash)
            } else {
                val sha1 = hashFileSha1("$mediaBase/${raw.relativePath}")
                mediaManager.updateCache(raw.relativePath, raw.mtime, sha1)
                MediaManifestEntry(raw.relativePath, raw.size, sha1)
            }
        }

        val localManifest = MediaManifest(version = 1, entries = resolved)
        syncManager.uploadManifest(config, mediaManager.manifestToJson(localManifest))

        val remoteResult = syncManager.downloadManifest(config)
        val remoteManifest = if (remoteResult.isSuccess) mediaManager.manifestFromJson(remoteResult.getOrThrow()) else null

        val needUpload = mediaManager.filesToUpload(resolved, remoteManifest)
        for (path in needUpload) {
            val absPath = "$mediaBase/$path"
            try {
                val data = java.io.File(absPath).readBytes()
                syncManager.uploadMedia(config, path, data).getOrThrow()
            } catch (_: Exception) { }
        }
    }

    // Font sync: sync user-imported font files across devices
    try {
        val fontDir = com.lumecard.app.font.getFontStorageDir()
        val fontDirFile = java.io.File(fontDir)
        val localFontFiles = fontDirFile.listFiles()?.filter { it.isFile }.orEmpty()

        val remoteListResult = syncManager.downloadFontManifest(config)
        val remoteFontNames = if (remoteListResult.isSuccess) {
            try {
                fontManifestJson.decodeFromString<List<String>>(remoteListResult.getOrThrow())
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val localFontNames = localFontFiles.map { it.name }.toSet()

        for (file in localFontFiles) {
            if (file.name !in remoteFontNames) {
                try {
                    syncManager.uploadFont(config, file.name, file.readBytes())
                } catch (_: Exception) { }
            }
        }

        for (name in remoteFontNames) {
            if (name !in localFontNames) {
                try {
                    val data = syncManager.downloadFont(config, name).getOrNull()
                    if (data != null) {
                        java.io.File(fontDirFile, name).writeBytes(data)
                    }
                } catch (_: Exception) { }
            }
        }

        val currentNames = fontDirFile.listFiles()?.map { it.name }.orEmpty()
        syncManager.uploadFontManifest(config, fontManifestJson.encodeToString(currentNames))
    } catch (_: Exception) { }

    return allDecks.size
}

private suspend fun restoreSettingsAndFonts(
    config: WebDavConfig,
    syncManager: SyncManager,
    settingsRepository: SettingsRepository,
) {
    try {
        val configResult = syncManager.downloadConfig(config)
        if (configResult.isSuccess) {
            val remoteSettings = try {
                fontManifestJson.decodeFromString<Map<String, String>>(configResult.getOrThrow())
            } catch (_: Exception) { null }
            if (remoteSettings != null) {
                for ((key, value) in remoteSettings) {
                    settingsRepository.set(key, value)
                }
            }
        }

        val fontDir = com.lumecard.app.font.getFontStorageDir()
        val fontDirFile = java.io.File(fontDir)
        if (!fontDirFile.exists()) fontDirFile.mkdirs()

        val remoteListResult = syncManager.downloadFontManifest(config)
        val remoteFontNames = if (remoteListResult.isSuccess) {
            try {
                fontManifestJson.decodeFromString<List<String>>(remoteListResult.getOrThrow())
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val localFontNames = fontDirFile.listFiles()?.map { it.name }.orEmpty().toSet()

        for (name in remoteFontNames) {
            if (name !in localFontNames) {
                try {
                    val data = syncManager.downloadFont(config, name).getOrNull()
                    if (data != null) {
                        java.io.File(fontDirFile, name).writeBytes(data)
                    }
                } catch (_: Exception) { }
            }
        }

        com.lumecard.app.font.FontRegistry.loadUserFonts(settingsRepository)
    } catch (_: Exception) { }
}
