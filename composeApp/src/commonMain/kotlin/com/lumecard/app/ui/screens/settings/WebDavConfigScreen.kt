package com.lumecard.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.lumecard.app.platform.platformGetUserHome
import com.lumecard.app.platform.platformGetSystemProperty
import com.lumecard.app.platform.platformJoinPath
import com.lumecard.app.platform.platformPathExists
import com.lumecard.app.platform.platformMkdirs
import com.lumecard.app.platform.platformListFileNames
import com.lumecard.app.platform.platformFileExists
import com.lumecard.app.platform.platformGetParentDir
import com.lumecard.app.platform.platformReadFileBytes
import com.lumecard.app.platform.platformReadFileText
import com.lumecard.app.platform.platformWriteFileBytes
import com.lumecard.app.platform.platformWriteFileText
import com.lumecard.app.platform.platformDeleteFile
import com.lumecard.app.platform.scanMediaDirectory
import com.lumecard.app.platform.scanMediaDirectoryRaw
import com.lumecard.app.ui.components.ContextHelpButton
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.DataExport
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.data.ExportSplashQuotes
import com.lumecard.shared.data.MediaManager
import com.lumecard.shared.data.SyncHistoryEntry
import com.lumecard.shared.data.MediaManifest
import com.lumecard.shared.data.MediaManifestEntry
import com.lumecard.shared.data.SplashQuoteData
import com.lumecard.shared.data.SplashQuoteManager
import com.lumecard.shared.crypto.SensitiveDataEncryptor
import com.lumecard.shared.crypto.SensitiveKeys
import com.lumecard.shared.data.FontManifestEntry
import com.lumecard.shared.data.SyncManager
import com.lumecard.shared.data.downloadFontManifestEntries
import com.lumecard.shared.data.generateUuid
import com.lumecard.shared.data.mergeFontEntries
import com.lumecard.shared.data.uploadFontManifestEntries
import com.lumecard.shared.data.WebDavConfig
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.shared.data.WebDavProviders
import com.lumecard.shared.data.toCard
import com.lumecard.shared.data.toDeck
import com.lumecard.shared.data.toKnowledgeBase
import com.lumecard.shared.data.toLearningPlan
import com.lumecard.shared.data.toReviewLog
import com.lumecard.shared.database.DatabaseDriverHolder
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
        val splashQuoteManager: SplashQuoteManager = koinInject()
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
        var isTesting by remember { mutableStateOf(false) }
        var isSyncing by remember { mutableStateOf(false) }
        var syncStatus by remember { mutableStateOf("") }
        var deleteConfirmId by remember { mutableStateOf<String?>(null) }
        var showForceUploadConfirm by remember { mutableStateOf(false) }
        var showForceDownloadConfirm by remember { mutableStateOf(false) }
        var showForceUploadConfigConfirm by remember { mutableStateOf(false) }
        var showForceDownloadConfigConfirm by remember { mutableStateOf(false) }
        var showRestoreHistory by remember { mutableStateOf(false) }
        var historyEntries by remember { mutableStateOf<List<SyncHistoryEntry>>(emptyList()) }
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
                    action = {
                        ContextHelpButton(articleId = "webdav-sync")
                    },
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

                            // Test result (copyable)
                            if (testResult != null) {
                                val isSuccess = testResult!!.startsWith("HTTP") || testResult!!.startsWith(strings.settingsSyncTestSuccess)
                                SelectionContainer {
                                    Text(
                                        testResult!!,
                                        color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isTesting = true
                                        testResult = null
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
                                                val result = withContext(Dispatchers.IO) { webDavConfigManager.testConnection(config) }
                                                testResult = result.fold(
                                                    onSuccess = { strings.settingsSyncTestSuccess },
                                                    onFailure = { strings.settingsSyncTestError(it.message ?: strings.errorUnknown) }
                                                )
                                            } catch (e: Exception) {
                                                testResult = strings.settingsSyncTestError(e.message ?: strings.errorUnknown)
                                            } finally {
                                                isTesting = false
                                            }
                                        }
                                    },
                                    enabled = editUrl.isNotBlank() && editUser.isNotBlank() && editPass.isNotBlank() && !isTesting,
                                    interactionSource = null,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(spacing.sm))
                                        Text(strings.settingsSyncTestConnecting)
                                    } else {
                                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(spacing.sm))
                                        Text(strings.settingsSyncTestConnection)
                                    }
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

                    // 数据（学习数据）
                    Text(
                        strings.syncScopeData,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Button(
                            onClick = { showForceUploadConfirm = true },
                            interactionSource = null,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.sm))
                            Text(strings.syncForceUpload, style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = { showForceDownloadConfirm = true },
                            interactionSource = null,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.sm))
                            Text(strings.syncForceDownload, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // 配置（设置）
                    Text(
                        strings.syncScopeConfig,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = { showForceUploadConfigConfirm = true },
                            interactionSource = null,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.sm))
                            Text(strings.syncForceUploadConfig, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { showForceDownloadConfigConfirm = true },
                            interactionSource = null,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.sm))
                            Text(strings.syncForceDownloadConfig, style = MaterialTheme.typography.labelMedium)
                        }
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

        // Restore a history entry: restore only data (snapshot merge) or only config (remote config + fonts).
        val restoreHistory: (SyncHistoryEntry, Boolean) -> Unit = { entry, restoreData ->
            showRestoreHistory = false
            scope.launch {
                isSyncing = true
                syncStatus = strings.settingsSyncing
                try {
                    val config = defaultConfig ?: return@launch
                    val result = withContext(Dispatchers.IO) {
                        if (restoreData) {
                            val remoteResult = syncManager.downloadSnapshot(config, entry.filename)
                            var restoredDecks = 0
                            if (remoteResult.isSuccess) {
                                val remote = exportManager.importData(remoteResult.getOrThrow())
                                if (remote != null) {
                                    restoredDecks = remote.decks.size
                                    writeMergedToLocal(remote, knowledgeBaseRepository, deckRepository, cardRepository, reviewLogRepository, planRepository)
                                }
                            }
                            webDavConfigManager.updateLastSync(config.id)
                            restoredDecks
                        } else {
                            restoreSettingsAndFonts(config, syncManager, settingsRepository)
                            webDavConfigManager.updateLastSync(config.id)
                            0
                        }
                    }
                    syncStatus = strings.settingsSyncSuccess(result)
                    snackbarHostState.showSnackbar(strings.settingsSyncSuccess(result))
                    reloadConfigs()
                } catch (e: Exception) {
                    syncStatus = strings.settingsSyncError(e.message ?: strings.errorUnknown)
                    snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: strings.errorUnknown))
                } finally {
                    isSyncing = false
                }
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
                                val displayName = entry.name ?: strings.syncHistoryEntryFormat(entry.timestamp, entry.deviceId)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = { restoreHistory(entry, true) },
                                                interactionSource = null,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.syncRestoreData, style = MaterialTheme.typography.labelSmall)
                                            }
                                            OutlinedButton(
                                                onClick = { restoreHistory(entry, false) },
                                                interactionSource = null,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.syncRestoreConfig, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
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

        // Force upload confirm dialog
        if (showForceUploadConfirm) {
            AlertDialog(
                onDismissRequest = { showForceUploadConfirm = false },
                title = { Text(strings.syncForceUploadConfirm) },
                text = { Text(strings.syncForceUploadConfirmDesc) },
                confirmButton = {
                    TextButton(onClick = {
                        showForceUploadConfirm = false
                        scope.launch {
                            isSyncing = true
                            syncStatus = strings.settingsSyncing
                            try {
                                val config = defaultConfig ?: return@launch
                                val deckCount: Int
                                withContext(Dispatchers.IO) {
                                    deckCount = forceUpload(config, knowledgeBaseRepository, deckRepository, cardRepository, reviewLogRepository, planRepository, exportManager, syncManager, mediaManager, splashQuoteManager)
                                    webDavConfigManager.updateLastSync(config.id)
                                }
                                syncStatus = strings.settingsSyncSuccess(deckCount)
                                snackbarHostState.showSnackbar(strings.settingsSyncSuccess(deckCount))
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
                    TextButton(onClick = { showForceUploadConfirm = false }, interactionSource = null) { Text(strings.actionCancel) }
                },
            )
        }

        // Force download confirm dialog
        if (showForceDownloadConfirm) {
            AlertDialog(
                onDismissRequest = { showForceDownloadConfirm = false },
                title = { Text(strings.syncForceDownloadConfirm) },
                text = { Text(strings.syncForceDownloadConfirmDesc) },
                confirmButton = {
                    TextButton(onClick = {
                        showForceDownloadConfirm = false
                        scope.launch {
                            isSyncing = true
                            syncStatus = strings.settingsSyncing
                            try {
                                val config = defaultConfig ?: return@launch
                                val deckCount: Int
                                withContext(Dispatchers.IO) {
                                    deckCount = forceDownload(config, knowledgeBaseRepository, deckRepository, cardRepository, reviewLogRepository, planRepository, exportManager, syncManager, mediaManager, splashQuoteManager)
                                    webDavConfigManager.updateLastSync(config.id)
                                }
                                syncStatus = strings.settingsSyncSuccess(deckCount)
                                snackbarHostState.showSnackbar(strings.settingsSyncSuccess(deckCount))
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
                    TextButton(onClick = { showForceDownloadConfirm = false }, interactionSource = null) { Text(strings.actionCancel) }
                },
            )
        }

        // Force upload config confirm dialog
        if (showForceUploadConfigConfirm) {
            AlertDialog(
                onDismissRequest = { showForceUploadConfigConfirm = false },
                title = { Text(strings.syncForceUploadConfigConfirm) },
                text = { Text(strings.syncForceUploadConfigConfirmDesc) },
                confirmButton = {
                    TextButton(onClick = {
                        showForceUploadConfigConfirm = false
                        scope.launch {
                            isSyncing = true
                            syncStatus = strings.settingsSyncing
                            try {
                                val config = defaultConfig ?: return@launch
                                withContext(Dispatchers.IO) {
                                    forceUploadConfig(config, exportManager, syncManager, settingsRepository)
                                    webDavConfigManager.updateLastSync(config.id)
                                }
                                syncStatus = strings.syncConfigSyncSuccess
                                snackbarHostState.showSnackbar(strings.syncConfigSyncSuccess)
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
                    TextButton(onClick = { showForceUploadConfigConfirm = false }, interactionSource = null) { Text(strings.actionCancel) }
                },
            )
        }

        // Force download config confirm dialog
        if (showForceDownloadConfigConfirm) {
            AlertDialog(
                onDismissRequest = { showForceDownloadConfigConfirm = false },
                title = { Text(strings.syncForceDownloadConfigConfirm) },
                text = { Text(strings.syncForceDownloadConfigConfirmDesc) },
                confirmButton = {
                    TextButton(onClick = {
                        showForceDownloadConfigConfirm = false
                        scope.launch {
                            isSyncing = true
                            syncStatus = strings.settingsSyncing
                            try {
                                val config = defaultConfig ?: return@launch
                                withContext(Dispatchers.IO) {
                                    forceDownloadConfig(config, syncManager, settingsRepository)
                                    webDavConfigManager.updateLastSync(config.id)
                                }
                                syncStatus = strings.syncConfigSyncSuccess
                                snackbarHostState.showSnackbar(strings.syncConfigSyncSuccess)
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
                    TextButton(onClick = { showForceDownloadConfigConfirm = false }, interactionSource = null) { Text(strings.actionCancel) }
                },
            )
        }

    }
}

private val fontManifestJson = Json { ignoreUnknownKeys = true }

private suspend fun writeMergedToLocal(
    data: DataExport,
    kbRepository: KnowledgeBaseRepository,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    reviewLogRepository: ReviewLogRepository,
    planRepository: LearningPlanRepository,
) {
    val localKbIds = kbRepository.getAll().first().map { it.id }.toSet()
    for (ekb in data.knowledgeBases) {
        val kb = ekb.toKnowledgeBase()
        if (kb.id in localKbIds) kbRepository.update(kb) else kbRepository.insert(kb)
    }

    val localDeckIds = deckRepository.getAll().first().map { it.id }.toSet()
    for (edeck in data.decks) {
        val deck = edeck.toDeck()
        if (deck.id in localDeckIds) deckRepository.update(deck) else deckRepository.insert(deck)
    }

    val localCardIds = cardRepository.getAll().first().map { it.id }.toSet()
    for (ecard in data.cards) {
        val card = ecard.toCard()
        if (card.id in localCardIds) cardRepository.update(card) else cardRepository.insert(card)
    }

    val localLogIds = reviewLogRepository.getAll().first().map { it.id }.toSet()
    for (elog in data.reviewLogs) {
        val log = elog.toReviewLog()
        if (log.id !in localLogIds) reviewLogRepository.insert(log)
    }

    val localPlanIds = planRepository.getAll().first().map { it.id }.toSet()
    for (eplan in data.learningPlans) {
        val plan = eplan.toLearningPlan()
        if (plan.id in localPlanIds) planRepository.update(plan) else planRepository.insert(plan)
    }
}

private suspend fun forceUpload(
    config: WebDavConfig,
    kbRepository: KnowledgeBaseRepository,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    reviewLogRepository: ReviewLogRepository,
    planRepository: LearningPlanRepository,
    exportManager: ExportManager,
    syncManager: SyncManager,
    mediaManager: MediaManager,
    splashQuoteManager: SplashQuoteManager? = null,
): Int {
    syncManager.archiveCurrentSnapshot(config, direction = "上行", type = "D")

    val allKbs = kbRepository.getAll().first()
    val allDecks = deckRepository.getAll().first()
    val allCards = cardRepository.getAll().first()
    val allLogs = reviewLogRepository.getAll().first()
    val allPlans = planRepository.getAll().first()

    val quotesExport = if (splashQuoteManager != null) {
        val (qv, qu, hi) = splashQuoteManager.getQuotesExportData()
        ExportSplashQuotes(qv, qu, hi)
    } else null

    val json = exportManager.exportData(allKbs, allDecks, allCards, allLogs, allPlans, quotesExport)
    syncManager.uploadData(config, json).getOrThrow()

    val now = Clock.System.now()
    kbRepository.markSynced(allKbs.map { it.id }, now)
    deckRepository.markSynced(allDecks.map { it.id }, now)
    cardRepository.markSynced(allCards.map { it.id }, now)
    reviewLogRepository.markSynced(allLogs.map { it.id }, now)
    planRepository.markSynced(allPlans.map { it.id }, now)

    syncMedia(config, syncManager, mediaManager)
    return allDecks.size
}

private suspend fun forceDownload(
    config: WebDavConfig,
    kbRepository: KnowledgeBaseRepository,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    reviewLogRepository: ReviewLogRepository,
    planRepository: LearningPlanRepository,
    exportManager: ExportManager,
    syncManager: SyncManager,
    mediaManager: MediaManager,
    splashQuoteManager: SplashQuoteManager? = null,
): Int {
    syncManager.archiveCurrentSnapshot(config, direction = "下行", type = "D")

    val remoteResult = syncManager.downloadData(config)
    if (remoteResult.isFailure) throw Exception("No remote data found")

    val remoteJson = remoteResult.getOrThrow()
    val remote = exportManager.importData(remoteJson) ?: throw Exception("Failed to parse remote data")

    val driver = DatabaseDriverHolder.driver
        ?: throw IllegalStateException("Database driver not initialized")

    // Disable FK constraints for the entire delete+insert operation.
    // This is necessary because:
    // 1. INSERT OR REPLACE internally does DELETE+INSERT; soft-deleted children
    //    still reference parent rows, blocking the DELETE.
    // 2. Deck has self-referencing FK (parent_id -> Deck.id); if export order
    //    puts a child deck before its parent, the INSERT would fail.
    driver.execute(null, "PRAGMA foreign_keys = OFF", 0, null)

    try {
        // Hard-delete ALL local data
        driver.execute(null, "DELETE FROM CardFTS", 0, null)
        driver.execute(null, "DELETE FROM AlgorithmState", 0, null)
        driver.execute(null, "DELETE FROM ReviewLog", 0, null)
        driver.execute(null, "DELETE FROM Card", 0, null)
        driver.execute(null, "DELETE FROM Deck", 0, null)
        driver.execute(null, "DELETE FROM KnowledgeBase", 0, null)
        driver.execute(null, "DELETE FROM LearningPlan", 0, null)

        // Insert remote data
        for (kb in remote.knowledgeBases) { kbRepository.insert(kb.toKnowledgeBase()) }
        for (deck in remote.decks) { deckRepository.insert(deck.toDeck()) }
        for (card in remote.cards) { cardRepository.insert(card.toCard()) }
        for (log in remote.reviewLogs) { reviewLogRepository.insert(log.toReviewLog()) }
        for (plan in remote.learningPlans) { planRepository.insert(plan.toLearningPlan()) }

        val remoteSplash = remote.splashQuotes
        if (remoteSplash != null && splashQuoteManager != null) {
            splashQuoteManager.importQuotesFromSync(
                remoteSplash.version, remoteSplash.userQuotes, remoteSplash.hiddenDefaultIndices,
            )
        }

        val now = Clock.System.now()
        kbRepository.markSynced(remote.knowledgeBases.map { it.id }, now)
        deckRepository.markSynced(remote.decks.map { it.id }, now)
        cardRepository.markSynced(remote.cards.map { it.id }, now)
        reviewLogRepository.markSynced(remote.reviewLogs.map { it.id }, now)
        planRepository.markSynced(remote.learningPlans.map { it.id }, now)
    } finally {
        // Always re-enable FK constraints
        driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
    }

    syncMedia(config, syncManager, mediaManager)
    return remote.decks.size
}

private suspend fun syncMedia(
    config: WebDavConfig,
    syncManager: SyncManager,
    mediaManager: MediaManager,
) {
    try {
        val userHome = platformGetUserHome() ?: ""
        val mediaBase = platformGetSystemProperty("lumecard.media.dir") ?: platformJoinPath(userHome, ".lumecard/media")
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
                    val data = platformReadFileBytes(absPath) ?: byteArrayOf()
                    syncManager.uploadMedia(config, path, data).getOrThrow()
                } catch (_: Exception) { }
            }
        }
    } catch (_: Exception) { }
}

private suspend fun syncFonts(
    config: WebDavConfig,
    syncManager: SyncManager,
    settingsRepository: SettingsRepository,
    forcePush: Boolean = false,
) {
    try {
        val fontDir = com.lumecard.app.font.getFontStorageDir()
        if (!platformPathExists(fontDir)) platformMkdirs(fontDir)

        val localFileNames = platformListFileNames(fontDir).toSet()

        // 1. Read local manifest + reconcile with filesystem
        val localManifest = readLocalFontManifest(fontDir)
        val reconciledLocal = reconcileLocalManifest(localManifest, localFileNames)

        // 2. Download remote manifest
        val remoteEntries = syncManager.downloadFontManifestEntries(config).getOrDefault(emptyList())

        // 3. Merge or override
        val merged = if (forcePush) {
            reconciledLocal
        } else {
            mergeFontEntries(reconciledLocal, remoteEntries)
        }

        // 4. List actual remote files
        val remoteFileNames = syncManager.listRemoteFonts(config).getOrDefault(emptyList()).toSet()

        // 5. Execute file operations for merged entries
        val mergedMutable = merged.toMutableList()
        val activeFileNames = merged.filter { it.deletedAt == null }.map { it.fileName }.toSet()
        for (entry in merged) {
            if (entry.deletedAt != null) {
                // Skip tombstone if a newer active entry has the same filename (font was re-imported)
                if (entry.fileName in activeFileNames) continue
                if (entry.fileName in localFileNames) {
                    platformDeleteFile(platformJoinPath(fontDir, entry.fileName))
                }
                try { syncManager.deleteFont(config, entry.fileName) } catch (_: Exception) { }
            } else {
                val localHas = entry.fileName in localFileNames
                val remoteHas = entry.fileName in remoteFileNames
                if (!localHas && remoteHas) {
                    try {
                        val data = syncManager.downloadFont(config, entry.fileName).getOrNull()
                        if (data != null) platformWriteFileBytes(platformJoinPath(fontDir, entry.fileName), data)
                    } catch (e: Exception) {
                        println("[LumeCard] font download failed: ${entry.fileName} - ${e.message}")
                    }
                } else if (localHas && !remoteHas) {
                    try {
                        val data = platformReadFileBytes(platformJoinPath(fontDir, entry.fileName)) ?: byteArrayOf()
                        syncManager.uploadFont(config, entry.fileName, data)
                    } catch (e: Exception) {
                        println("[LumeCard] font upload failed: ${entry.fileName} - ${e.message}")
                    }
                }
            }
        }

        // 6. Handle remote orphan files: exist on remote but not tracked in merged manifest
        for (name in remoteFileNames) {
            if (name !in activeFileNames && name !in localFileNames) {
                try {
                    val data = syncManager.downloadFont(config, name).getOrNull()
                    if (data != null) {
                        platformWriteFileBytes(platformJoinPath(fontDir, name), data)
                        val now = Clock.System.now().toString()
                        mergedMutable.add(FontManifestEntry(
                            id = generateUuid(),
                            fileName = name,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                        ))
                    }
                } catch (e: Exception) {
                    println("[LumeCard] font download (orphan) failed: $name - ${e.message}")
                }
            }
        }

        // 7. Write merged manifest to both sides
        writeLocalFontManifest(fontDir, mergedMutable)
        syncManager.uploadFontManifestEntries(config, mergedMutable)

        // 7. Reload font registry
        com.lumecard.app.font.FontRegistry.rebuildFromStorageDir(settingsRepository)
    } catch (e: Exception) {
        println("[LumeCard] font sync error: ${e.message}")
    }
}

private fun localFontManifestFile(fontDir: String): String {
    return platformJoinPath(platformGetParentDir(fontDir), "font_registry.json")
}

private fun readLocalFontManifest(fontDir: String): List<FontManifestEntry> {
    val filePath = localFontManifestFile(fontDir)
    if (!platformFileExists(filePath)) return emptyList()
    return try {
        val text = platformReadFileText(filePath) ?: return emptyList()
        fontManifestJson.decodeFromString<List<FontManifestEntry>>(text)
    } catch (_: Exception) { emptyList() }
}

private fun writeLocalFontManifest(fontDir: String, entries: List<FontManifestEntry>) {
    val filePath = localFontManifestFile(fontDir)
    platformMkdirs(platformGetParentDir(filePath))
    platformWriteFileText(filePath, fontManifestJson.encodeToString(entries))
}

private fun reconcileLocalManifest(
    manifest: List<FontManifestEntry>,
    currentFileNames: Set<String>,
): MutableList<FontManifestEntry> {
    val result = manifest.toMutableList()
    val now = Clock.System.now().toString()

    // Detect new fonts not tracked by manifest
    for (fileName in currentFileNames) {
        val tracked = result.any { it.fileName == fileName && it.deletedAt == null }
        if (!tracked) {
            result.add(FontManifestEntry(
                id = generateUuid(),
                fileName = fileName,
                version = 1,
                createdAt = now,
                updatedAt = now,
            ))
        }
    }

    // Detect deleted fonts (in manifest but file missing) → create tombstones
    val indicesToRemove = mutableListOf<Int>()
    val entriesToAdd = mutableListOf<FontManifestEntry>()
    for ((i, entry) in result.withIndex()) {
        if (entry.deletedAt == null && entry.fileName !in currentFileNames) {
            indicesToRemove.add(i)
            entriesToAdd.add(entry.copy(
                version = entry.version + 1,
                updatedAt = now,
                deletedAt = now,
            ))
        }
    }
    for (i in indicesToRemove.reversed()) result.removeAt(i)
    result.addAll(entriesToAdd)

    return result
}

private suspend fun downloadRemoteSettings(
    config: WebDavConfig,
    syncManager: SyncManager,
): Map<String, String>? {
    val configResult = syncManager.downloadConfig(config)
    if (configResult.isFailure) return null
    val remoteSettings = try {
        fontManifestJson.decodeFromString<com.lumecard.shared.data.ConfigExport>(configResult.getOrThrow()).settings
    } catch (_: Exception) {
        try {
            fontManifestJson.decodeFromString<Map<String, String>>(configResult.getOrThrow())
        } catch (_: Exception) { null }
    }
    if (remoteSettings == null) return null
    val encryptor = SensitiveDataEncryptor(config.password)
    return encryptor.decryptSettings(remoteSettings)
}

private suspend fun forceUploadConfig(
    config: WebDavConfig,
    exportManager: ExportManager,
    syncManager: SyncManager,
    settingsRepository: SettingsRepository,
) {
    val encryptor = SensitiveDataEncryptor(config.password)
    val settings = encryptor.encryptSettings(settingsRepository.getAll())
    val configJson = exportManager.exportConfig(settings)
    syncManager.uploadConfig(config, configJson).getOrThrow()

    // 字体归配置：上传本地字体（覆盖远端）
    syncFonts(config, syncManager, settingsRepository, forcePush = true)
}

private suspend fun forceDownloadConfig(
    config: WebDavConfig,
    syncManager: SyncManager,
    settingsRepository: SettingsRepository,
) {
    val remoteSettings = downloadRemoteSettings(config, syncManager)
        ?: throw Exception("No remote config found")
    for (key in settingsRepository.getAll().keys) {
        settingsRepository.delete(key)
    }
    for ((key, value) in remoteSettings) {
        settingsRepository.set(key, value)
    }

    // 字体归配置：下载远端字体到本地
    syncFonts(config, syncManager, settingsRepository, forcePush = false)
}

private suspend fun restoreSettingsAndFonts(
    config: WebDavConfig,
    syncManager: SyncManager,
    settingsRepository: SettingsRepository,
) {
    try {
        val remoteSettings = downloadRemoteSettings(config, syncManager)
        if (remoteSettings != null) {
            for ((key, value) in remoteSettings) {
                settingsRepository.set(key, value)
            }
        }

        val fontDir = com.lumecard.app.font.getFontStorageDir()
        if (!platformPathExists(fontDir)) platformMkdirs(fontDir)

        val localFileNames = platformListFileNames(fontDir).toSet()

        // Sync font files: merge remote state into local
        val localEntries = readLocalFontManifest(fontDir)
        val reconciledLocal = reconcileLocalManifest(localEntries, localFileNames)
        val remoteEntries = syncManager.downloadFontManifestEntries(config).getOrDefault(emptyList())
        val merged = mergeFontEntries(reconciledLocal, remoteEntries).toMutableList()
        val remoteFileNames = syncManager.listRemoteFonts(config).getOrDefault(emptyList()).toSet()

        // Download fonts tracked in merged manifest
        for (entry in merged) {
            if (entry.deletedAt == null && entry.fileName !in localFileNames && entry.fileName in remoteFileNames) {
                try {
                    val data = syncManager.downloadFont(config, entry.fileName).getOrNull()
                    if (data != null) {
                        platformWriteFileBytes(platformJoinPath(fontDir, entry.fileName), data)
                    }
                } catch (_: Exception) { }
            }
        }

        // Download orphan remote fonts not tracked in manifest
        val activeNames = merged.filter { it.deletedAt == null }.map { it.fileName }.toSet()
        for (name in remoteFileNames) {
            if (name !in activeNames && name !in localFileNames) {
                try {
                    val data = syncManager.downloadFont(config, name).getOrNull()
                    if (data != null) {
                        platformWriteFileBytes(platformJoinPath(fontDir, name), data)
                        val now = Clock.System.now().toString()
                        merged.add(FontManifestEntry(
                            id = generateUuid(),
                            fileName = name,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                        ))
                    }
                } catch (_: Exception) { }
            }
        }

        writeLocalFontManifest(fontDir, merged)
        com.lumecard.app.font.FontRegistry.rebuildFromStorageDir(settingsRepository)
    } catch (_: Exception) { }
}
