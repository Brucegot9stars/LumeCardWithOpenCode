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
import com.lumecard.app.platform.scanMediaDirectory
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.data.MediaManager
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.compose.koinInject

class WebDavConfigScreen : Screen {
    override val key: ScreenKey = "WebDavConfig"

    @OptIn(ExperimentalMaterial3Api::class)
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
        var autoSyncEnabled by remember { mutableStateOf(false) }
        var autoSyncInterval by remember { mutableStateOf(30) }
        var showIntervalDropdown by remember { mutableStateOf(false) }
        var defaultConfig by remember { mutableStateOf<WebDavConfig?>(null) }

        val localeCode = i18nManager.currentLocale.code
        val providerPresets = WebDavProviders.all.map { provider ->
            Triple(WebDavProviders.getName(provider, localeCode), provider.urlTemplate, provider.id)
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

        LaunchedEffect(Unit) { reloadConfigs() }

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
                            val detectedProvider = remember(editUrl) {
                                if (editUrl.isNotBlank()) WebDavProviders.detectProvider(editUrl) else null
                            }
                            val displayProviderName = remember(editUrl, detectedProvider) {
                                if (detectedProvider != null) {
                                    WebDavProviders.getName(detectedProvider, localeCode)
                                } else {
                                    val matched = providerPresets.firstOrNull { it.second == editUrl }
                                    matched?.first ?: strings.webdavProviderCustom
                                }
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
                                    providerPresets.forEach { (name, url, _) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                if (url.isNotEmpty()) {
                                                    editUrl = url
                                                    if (editName.isBlank()) editName = name
                                                }
                                                showProviderMenu = false
                                            },
                                        )
                                    }
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
                                    TextButton(onClick = { showPass = !showPass }) {
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
                                                onFailure = { strings.settingsSyncTestError(it.message ?: "Unknown") }
                                            )
                                            val msg = testResult ?: return@launch
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    },
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
                                                snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                                            }
                                        }
                                    },
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
                                                            snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
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
                                    onCheckedChange = { autoSyncEnabled = it },
                                )
                            }
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
                                    withContext(Dispatchers.IO) {
                                        syncIncrementalData(config, knowledgeBaseRepository, deckRepository, cardRepository, reviewLogRepository, planRepository, exportManager, syncManager)
                                        val settings = settingsRepository.getAll()
                                        val configJson = exportManager.exportConfig(settings)
                                        syncManager.uploadConfig(config, configJson).getOrThrow()
                                        webDavConfigManager.updateLastSync(config.id)
                                    }
                                    syncStatus = strings.settingsSyncSuccess(0)
                                    snackbarHostState.showSnackbar(strings.settingsSyncSuccess(0))
                                    reloadConfigs()
                                } catch (e: Exception) {
                                    syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                    snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
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
                                        withContext(Dispatchers.IO) {
                                            syncIncrementalData(config, knowledgeBaseRepository, deckRepository, cardRepository, reviewLogRepository, planRepository, exportManager, syncManager)
                                            webDavConfigManager.updateLastSync(config.id)
                                        }
                                        syncStatus = strings.settingsSyncSuccess(0)
                                        snackbarHostState.showSnackbar(strings.settingsSyncSuccess(0))
                                    } catch (e: Exception) {
                                        syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                        snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
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
                                        syncStatus = strings.settingsSyncSuccess(0)
                                        snackbarHostState.showSnackbar(strings.settingsSyncSuccess(0))
                                    } catch (e: Exception) {
                                        syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                        snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                        ) {
                            Text(strings.settingsSyncConfig)
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.sm))

                    // Sync Media
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                syncStatus = strings.settingsSyncing
                                try {
                                    val config = defaultConfig ?: return@launch
                                    withContext(Dispatchers.IO) {
                                        val mediaBase = System.getProperty("lumecard.media.dir") ?: "${System.getProperty("user.home")}/.lumecard/media"
                                        val localFiles = scanMediaDirectory(mediaBase)
                                        val localManifest = MediaManifest(
                                            version = 1,
                                            entries = localFiles.map { MediaManifestEntry(it.relativePath, it.size, it.hash) }
                                        )
                                        syncManager.uploadManifest(config, mediaManager.manifestToJson(localManifest))

                                        val remoteResult = syncManager.downloadManifest(config)
                                        val remoteManifest = if (remoteResult.isSuccess) mediaManager.manifestFromJson(remoteResult.getOrThrow()) else null

                                        val needUpload = if (remoteManifest != null) {
                                            mediaManager.diffLocalVsRemote(localManifest, remoteManifest)
                                        } else {
                                            localFiles.map { it.relativePath }
                                        }

                                        var uploaded = 0
                                        for (path in needUpload) {
                                            val entry = localFiles.find { it.relativePath == path } ?: continue
                                            val absPath = "$mediaBase/$path"
                                            try {
                                                val data = java.io.File(absPath).readBytes()
                                                syncManager.uploadMedia(config, path, data).getOrThrow()
                                                uploaded++
                                            } catch (_: Exception) { }
                                        }
                                        syncStatus = "Media synced: $uploaded files"
                                    }
                                } catch (e: Exception) {
                                    syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing,
                    ) {
                        Text("Sync Media")
                    }

                    // Restore from cloud
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(strings.settingsRestoreFromCloud)
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
                                snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                            }
                        }
                    }) { Text(strings.actionConfirm) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmId = null }) { Text(strings.actionCancel) }
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
                    }) { Text(strings.actionConfirm) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) { Text(strings.actionCancel) }
                },
            )
        }
    }
}

private suspend fun syncIncrementalData(
    config: WebDavConfig,
    kbRepository: KnowledgeBaseRepository,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    reviewLogRepository: ReviewLogRepository,
    planRepository: LearningPlanRepository,
    exportManager: ExportManager,
    syncManager: SyncManager
) {
    val now = Clock.System.now()
    val since = config.lastSyncAt?.let { try { Instant.parse(it) } catch (_: Exception) { null } }

    if (since == null) {
        val kbs = kbRepository.getAll().first()
        val decks = deckRepository.getAll().first()
        val cards = cardRepository.getAll().first()
        val logs = reviewLogRepository.getAll().first()
        val plans = planRepository.getAll().first()
        val json = exportManager.exportData(kbs, decks, cards, logs, plans)
        syncManager.uploadData(config, json).getOrThrow()
        kbRepository.markSynced(kbs.map { it.id }, now)
        deckRepository.markSynced(decks.map { it.id }, now)
        cardRepository.markSynced(cards.map { it.id }, now)
        reviewLogRepository.markSynced(logs.map { it.id }, now)
        planRepository.markSynced(plans.map { it.id }, now)
        return
    }

    val dirtyKbs = kbRepository.getUpdatedSince(since)
    val dirtyDecks = deckRepository.getUpdatedSince(since)
    val dirtyCards = cardRepository.getUpdatedSince(since)
    val dirtyLogs = reviewLogRepository.getUpdatedSince(since)
    val dirtyPlans = planRepository.getUpdatedSince(since)

    if (dirtyKbs.isEmpty() && dirtyDecks.isEmpty() && dirtyCards.isEmpty() && dirtyLogs.isEmpty() && dirtyPlans.isEmpty()) {
        return
    }

    val json = exportManager.exportIncrementalData(
        knowledgeBases = dirtyKbs,
        decks = dirtyDecks,
        cards = dirtyCards,
        reviewLogs = dirtyLogs,
        learningPlans = dirtyPlans,
        since = since.toString()
    )
    syncManager.uploadData(config, json).getOrThrow()

    kbRepository.markSynced(dirtyKbs.map { it.id }, now)
    deckRepository.markSynced(dirtyDecks.map { it.id }, now)
    cardRepository.markSynced(dirtyCards.map { it.id }, now)
    reviewLogRepository.markSynced(dirtyLogs.map { it.id }, now)
    planRepository.markSynced(dirtyPlans.map { it.id }, now)
}
