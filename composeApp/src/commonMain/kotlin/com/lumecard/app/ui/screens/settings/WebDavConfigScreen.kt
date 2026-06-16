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
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.data.SyncManager
import com.lumecard.shared.data.WebDavConfig
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.shared.data.WebDavProviders
import com.lumecard.shared.data.toCard
import com.lumecard.shared.data.toDeck
import com.lumecard.shared.data.toKnowledgeBase
import com.lumecard.shared.data.toReviewLog
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
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
        val deckRepository: DeckRepository = koinInject()
        val cardRepository: CardRepository = koinInject()
        val knowledgeBaseRepository: com.lumecard.shared.repository.KnowledgeBaseRepository = koinInject()
        val reviewLogRepository: ReviewLogRepository = koinInject()
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
        var syncScope by remember { mutableStateOf(SyncScope.ALL) }
        var autoSyncEnabled by remember { mutableStateOf(false) }
        var autoSyncInterval by remember { mutableStateOf(30) }
        var showIntervalDropdown by remember { mutableStateOf(false) }
        var showScopeDropdown by remember { mutableStateOf(false) }
        var defaultConfig by remember { mutableStateOf<WebDavConfig?>(null) }

        val localeCode = i18nManager.currentLocale.code
        val providerPresets = WebDavProviders.all.map { provider ->
            Triple(WebDavProviders.getName(provider, localeCode), provider.urlTemplate, provider.id)
        }
        val customPreset = Triple(strings.webdavProviderCustom, "", "custom")

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
                    // Editing / Adding config
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
                    // Config list
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

                // Sync Scope
                if (!isEditing) {
                    Text(
                        strings.settingsSyncScope,
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
                            SyncScope.entries.forEach { scope ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { syncScope = scope }
                                        .padding(horizontal = spacing.md, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = syncScope == scope,
                                        onClick = { syncScope = scope },
                                    )
                                    Spacer(modifier = Modifier.width(spacing.sm))
                                    Text(
                                        when (scope) {
                                            SyncScope.ALL -> strings.settingsSyncScopeAll
                                            SyncScope.SETTINGS -> strings.settingsSyncScopeSettings
                                            SyncScope.DATA -> strings.settingsSyncScopeData
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (scope != SyncScope.DATA) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.md))
                                }
                            }
                        }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    syncStatus = strings.settingsSyncing
                                    try {
                                        val config = defaultConfig ?: return@launch
                                        val syncResult = withContext(Dispatchers.IO) {
                                            val localKbs = knowledgeBaseRepository.getAll().first()
                                            val localDecks = deckRepository.getAll().first()
                                            val localCards = cardRepository.getAll().first()
                                            val localLogs = reviewLogRepository.getAll().first()
                                            val settings = settingsRepository.getAll()
                                            syncManager.performSync(
                                                config = config,
                                                localKnowledgeBases = localKbs,
                                                localDecks = localDecks,
                                                localCards = localCards,
                                                localReviewLogs = localLogs,
                                                localSettings = settings,
                                                exportManager = exportManager,
                                            )
                                        }
                                        when (syncResult) {
                                            is com.lumecard.shared.data.SyncResult.Success -> {
                                                val msg = strings.settingsSyncSuccess(syncResult.decksSynced)
                                                syncStatus = msg
                                                snackbarHostState.showSnackbar(msg)
                                                reloadConfigs()
                                            }
                                            is com.lumecard.shared.data.SyncResult.RemoteImport -> {
                                                val export = syncResult.export
                                                withContext(Dispatchers.IO) {
                                                    for (kb in export.knowledgeBases) {
                                                        knowledgeBaseRepository.insert(kb.toKnowledgeBase())
                                                    }
                                                    for (deck in export.decks) {
                                                        deckRepository.insert(deck.toDeck())
                                                    }
                                                    for (card in export.cards) {
                                                        cardRepository.insert(card.toCard())
                                                    }
                                                    for (log in export.reviewLogs) {
                                                        reviewLogRepository.insert(log.toReviewLog())
                                                    }
                                                }
                                                val msg = strings.settingsSyncSuccess(export.decks.size)
                                                syncStatus = msg
                                                snackbarHostState.showSnackbar(msg)
                                                reloadConfigs()
                                            }
                                            is com.lumecard.shared.data.SyncResult.Skipped -> {
                                                syncStatus = syncResult.reason
                                            }
                                            is com.lumecard.shared.data.SyncResult.Error -> {
                                                val msg = strings.settingsSyncError(syncResult.message)
                                                syncStatus = msg
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
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
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(spacing.sm))
                            Text(strings.settingsSyncNow)
                        }
                    }

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
                                        val remote = exportManager.importFromJson(remoteResult.getOrThrow())
                                        if (remote != null) {
                                            val existingDecks = deckRepository.getAll().first()
                                            for (deck in existingDecks) {
                                                deckRepository.delete(deck.id)
                                            }
                                            val existingCards = cardRepository.getAll().first()
                                            for (card in existingCards) {
                                                cardRepository.delete(card.id)
                                            }
                                            for (deck in remote.decks) {
                                                val now = Clock.System.now()
                                                deckRepository.insert(
                                                    Deck(
                                                        id = deck.id, knowledgeBaseId = "default",
                                                        name = deck.name, description = deck.description ?: "",
                                                        color = deck.color, icon = deck.icon,
                                                        createdAt = now, updatedAt = now,
                                                    )
                                                )
                                                restoredDecks++
                                            }
                                            for (card in remote.cards) {
                                                cardRepository.insert(
                                                    Card(
                                                        id = card.id, deckId = card.deckId,
                                                        type = CardType.valueOf(card.type),
                                                        front = card.front, back = card.back,
                                                        tags = card.tags,
                                                        createdAt = Instant.parse(card.createdAt),
                                                        updatedAt = Instant.parse(card.updatedAt),
                                                    )
                                                )
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

private enum class SyncScope {
    ALL, SETTINGS, DATA
}
