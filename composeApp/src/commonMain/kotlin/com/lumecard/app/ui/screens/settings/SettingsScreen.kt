package com.lumecard.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.data.SyncManager
import com.lumecard.shared.data.WebDavConfig
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.shared.domain.scheduler.ReviewMode
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import com.lumecard.app.i18n.AppLocale
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.i18n.I18nManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class SettingsScreen : Screen {
    override val key: ScreenKey = "Settings"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val settingsViewModel: SettingsViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val settingsState = settingsViewModel.state
        val exportManager: ExportManager = koinInject()
        val syncManager: SyncManager = koinInject()
        val deckRepository: DeckRepository = koinInject()
        val cardRepository: CardRepository = koinInject()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val webDavConfigManager: WebDavConfigManager = koinInject()

        var showSyncDialog by remember { mutableStateOf(false) }
        var syncStatus by remember { mutableStateOf("") }
        var defaultConfigName by remember { mutableStateOf("") }
        var showModeDropdown by remember { mutableStateOf(false) }
        var showAnswerModeDropdown by remember { mutableStateOf(false) }
        var showGoalDialog by remember { mutableStateOf(false) }
        var goalInput by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            settingsViewModel.loadSettings()
            try { withContext(Dispatchers.IO) { webDavConfigManager.getDefault() }?.let { defaultConfigName = it.name } } catch (_: Exception) { }
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.settingsTitle,
                    onBack = { navigator.pop() },
                    action = {
                        if (settingsState.isDirty) {
                            FilledTonalButton(
                                onClick = { settingsViewModel.saveSettings() },
                                enabled = !settingsState.isSaving,
                                modifier = Modifier.padding(end = 0.dp)
                            ) {
                                if (settingsState.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(strings.actionSave)
                                }
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // === 学习设置 ===
                SettingsSection(title = strings.settingsLearning) {
                    // Daily goal
                    ListItem(
                        headlineContent = { Text(strings.settingsDailyGoal) },
                        supportingContent = { Text(strings.settingsDailyGoalDesc) },
                        trailingContent = {
                            TextButton(onClick = {
                                goalInput = settingsState.dailyGoal.toString()
                                showGoalDialog = true
                            }) {
                                Text(
                                    strings.settingsDailyGoalValue(settingsState.dailyGoal),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    HorizontalDivider()

                    // New cards per day
                    ListItem(
                        headlineContent = { Text(strings.settingsNewCards) },
                        supportingContent = { Text(strings.settingsNewCardsDesc) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(onClick = {
                                    val v = (settingsState.newCardsPerDay - 5).coerceAtLeast(1)
                                    settingsViewModel.setNewCardsPerDay(v)
                                }, modifier = Modifier.size(32.dp)) {
                                    Text("-", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "${settingsState.newCardsPerDay}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                FilledIconButton(onClick = {
                                    val v = (settingsState.newCardsPerDay + 5).coerceAtMost(999)
                                    settingsViewModel.setNewCardsPerDay(v)
                                }, modifier = Modifier.size(32.dp)) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    )
                    HorizontalDivider()

                    // Review mode dropdown
                    ListItem(
                        headlineContent = { Text(strings.settingsReviewMode) },
                        supportingContent = { Text(reviewModeDesc(settingsState.reviewMode)) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showModeDropdown = true }) {
                                    Text(
                                        reviewModeName(settingsState.reviewMode),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = showModeDropdown,
                                    onDismissRequest = { showModeDropdown = false }
                                ) {
                                    ReviewMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                    Column {
                                                        Text(reviewModeName(mode), style = MaterialTheme.typography.bodyLarge)
                                                        Text(reviewModeDesc(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                onClick = {
                                                    settingsViewModel.setReviewMode(mode)
                                                showModeDropdown = false
                                            },
                                            leadingIcon = {
                                                if (mode == settingsState.reviewMode) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                SettingsSection(title = strings.settingsAnswerDisplay) {
                    ListItem(
                        headlineContent = { Text(strings.settingsAnswerMode) },
                        supportingContent = { Text(answerDisplayDesc(settingsState.answerDisplayMode)) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showAnswerModeDropdown = true }) {
                                    Text(
                                        answerDisplayName(settingsState.answerDisplayMode),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = showAnswerModeDropdown,
                                    onDismissRequest = { showAnswerModeDropdown = false }
                                ) {
                                        AnswerDisplayMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(answerDisplayName(mode), style = MaterialTheme.typography.bodyLarge)
                                                        Text(answerDisplayDesc(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                onClick = {
                                                    settingsViewModel.setAnswerDisplayMode(mode)
                                                showAnswerModeDropdown = false
                                            },
                                            leadingIcon = {
                                                if (mode == settingsState.answerDisplayMode) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // === 外观 ===
                SettingsSection(title = strings.settingsAppearance) {
                    ListItem(
                        headlineContent = { Text(strings.settingsDarkMode) },
                        supportingContent = { Text(strings.settingsDarkModeDesc) },
                        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settingsState.isDarkMode,
                                onCheckedChange = { settingsViewModel.setDarkMode(it) }
                            )
                        }
                    )
                }

                // === 语言 ===
                SettingsSection(title = strings.settingsLanguage) {
                    var showLangDropdown by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(strings.settingsLanguageDesc) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showLangDropdown = true }) {
                                    Text(
                                        when (settingsState.language) {
                                            AppLocale.SYSTEM -> strings.langSystem
                                            AppLocale.ZH_CN -> " "
                                            AppLocale.ZH_TW -> " "
                                            AppLocale.EN -> " "
                                            AppLocale.JA -> " "
                                            AppLocale.ES -> " "
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = showLangDropdown,
                                    onDismissRequest = { showLangDropdown = false }
                                ) {
                                    AppLocale.entries.forEach { locale ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (locale) {
                                                        AppLocale.SYSTEM -> "\u2699\uFE0F "
                                                        AppLocale.ZH_CN -> " "
                                                        AppLocale.ZH_TW -> " "
                                                        AppLocale.EN -> " "
                                                        AppLocale.JA -> " "
                                                        AppLocale.ES -> " "
                                                    }
                                                )
                                            },
                                            onClick = {
                                                settingsViewModel.setLanguage(locale)
                                                showLangDropdown = false
                                            },
                                            leadingIcon = {
                                                if (locale == settingsState.language) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // === 通知 ===
                SettingsSection(title = strings.settingsNotifications) {
                    ListItem(
                        headlineContent = { Text(strings.settingsDailyReminder) },
                        supportingContent = { Text(strings.settingsDailyReminderDesc) },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settingsState.notificationsEnabled,
                                onCheckedChange = { settingsViewModel.setNotifications(it) }
                            )
                        }
                    )
                }

                // === 数据管理 ===
                SettingsSection(title = strings.settingsDataManagement) {
                    ListItem(
                        headlineContent = { Text(strings.settingsExport) },
                        supportingContent = { Text(strings.settingsExportDesc) },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    val decks = deckRepository.getAll().first()
                                    val allCards = cardRepository.getAll().first()
                                    val json = exportManager.exportToJson(decks, allCards)
                                    snackbarHostState.showSnackbar(strings.settingsExportSuccess(json.length))
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(strings.settingsExportError(e.message!!))
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.settingsImport) },
                        supportingContent = { Text(strings.settingsImportDesc) },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    snackbarHostState.showSnackbar(strings.settingsImportHint)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(strings.settingsImportError(e.message!!))
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.settingsCloudSync) },
                        supportingContent = {
                            Text(
                                if (defaultConfigName.isNotEmpty()) "$defaultConfigName | ${strings.settingsCloudSyncDesc}"
                                else strings.settingsCloudSyncDesc
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        modifier = Modifier.clickable { showSyncDialog = true },
                        trailingContent = {
                            TextButton(onClick = { showSyncDialog = true }) {
                                Text(
                                    if (defaultConfigName.isNotEmpty()) defaultConfigName
                                    else strings.settingsSyncNotConfigured,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                // === 学习进度 ===
                SettingsSection(title = strings.settingsTodayProgress) {
                    @Composable
                    fun GoalProgress(label: String, current: Int, target: Int) {
                        val progress = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(strings.settingsProgressText(current, target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                    GoalProgress(strings.settingsTodayCompleted, current = 0, target = settingsState.dailyGoal)
                    HorizontalDivider()
                    GoalProgress(strings.settingsNewCardsLearned, current = 0, target = settingsState.newCardsPerDay)
                }

                // === 关于 ===
                SettingsSection(title = strings.settingsAbout) {
                    ListItem(
                        headlineContent = { Text(strings.settingsVersion) },
                        trailingContent = { Text(strings.settingsVersionNumber) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.settingsAboutApp) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showGoalDialog) {
            AlertDialog(
                onDismissRequest = { showGoalDialog = false },
                title = { Text(strings.settingsGoalDialogTitle) },
                text = {
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it.filter { c -> c.isDigit() } },
                        label = { Text(strings.settingsCardCount) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        goalInput.toIntOrNull()?.let { settingsViewModel.setDailyGoal(it) }
                        showGoalDialog = false
                    }) {
                        Text(strings.actionOk)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoalDialog = false }) {
                        Text(strings.actionCancel)
                    }
                }
            )
        }

        // Sync config dialog
        if (showSyncDialog) {
            var dialogConfigs by remember { mutableStateOf<List<WebDavConfig>>(emptyList()) }
            var isEditing by remember { mutableStateOf(false) }
            var editConfig by remember { mutableStateOf<WebDavConfig?>(null) }
            var editName by remember { mutableStateOf("") }
            var editUrl by remember { mutableStateOf("") }
            var editUser by remember { mutableStateOf("") }
            var editPass by remember { mutableStateOf("") }
            var showPass by remember { mutableStateOf(false) }
            var testResult by remember { mutableStateOf<String?>(null) }
            var isSyncing by remember { mutableStateOf(false) }
            var deleteConfirmId by remember { mutableStateOf<String?>(null) }

            fun reloadConfigs() {
                scope.launch {
                    try {
                        val configs = withContext(Dispatchers.IO) { webDavConfigManager.getAll() }
                        dialogConfigs = configs
                        val d = withContext(Dispatchers.IO) { webDavConfigManager.getDefault() }
                        if (d != null) defaultConfigName = d.name
                    } catch (_: Exception) { }
                }
            }

            LaunchedEffect(Unit) { reloadConfigs() }

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
                    }
                )
            }

            AlertDialog(
                onDismissRequest = { if (!isEditing) showSyncDialog = false },
                title = {
                    Text(
                        if (isEditing) {
                            if (editConfig != null) strings.actionEdit else strings.settingsSyncAddConfig
                        } else strings.settingsSyncDialogTitle
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(strings.settingsSyncConfigName) },
                                placeholder = { Text(strings.settingsSyncConfigNamePlaceholder) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                label = { Text(strings.settingsWebdavUrl) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editUser,
                                onValueChange = { editUser = it },
                                label = { Text(strings.settingsWebdavUser) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editPass,
                                onValueChange = { editPass = it },
                                label = { Text(strings.settingsWebdavPass) },
                                singleLine = true,
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (testResult != null) {
                                val isSuccess = testResult!!.startsWith("HTTP") || testResult!!.startsWith(strings.settingsSyncTestSuccess)
                                Text(
                                    testResult!!,
                                    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            if (syncStatus.isNotEmpty()) {
                                Text(syncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (isSyncing) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                            }
                            if (dialogConfigs.isEmpty()) {
                                Text(
                                    strings.settingsSyncNotConfigured,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                            dialogConfigs.forEach { config ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text(config.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                            if (config.isDefault) {
                                                Text(
                                                    strings.settingsSyncDefault,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            config.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val lastSync = config.lastSyncAt
                                        Text(
                                            if (lastSync != null) "${strings.settingsSyncLastSync}: ${lastSync.take(10)}"
                                            else strings.settingsSyncNever,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    editConfig = config
                                                    editName = config.name
                                                    editUrl = config.url
                                                    editUser = config.username
                                                    editPass = config.password
                                                    testResult = null
                                                    isEditing = true
                                                },
                                                modifier = Modifier.height(32.dp)
                                            ) { Text(strings.actionEdit, style = MaterialTheme.typography.labelMedium) }
                                            TextButton(
                                                onClick = { deleteConfirmId = config.id },
                                                modifier = Modifier.height(32.dp)
                                            ) { Text(strings.actionDelete, style = MaterialTheme.typography.labelMedium) }
                                            if (!config.isDefault) {
                                                TextButton(
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
                                                    modifier = Modifier.height(32.dp)
                                                ) { Text(strings.settingsSyncSetDefault, style = MaterialTheme.typography.labelMedium) }
                                            }
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            testResult = null
                                                            val result = withContext(Dispatchers.IO) { webDavConfigManager.testConnection(config) }
                                                            testResult = result.fold(
                                                                onSuccess = { strings.settingsSyncTestSuccess },
                                                                onFailure = { strings.settingsSyncTestError(it.message ?: "Unknown") }
                                                            )
                                                            val msg = testResult ?: return@launch
                                                            snackbarHostState.showSnackbar(msg)
                                                        } catch (e: Exception) {
                                                            val errMsg = strings.settingsSyncTestError(e.message ?: "Unknown")
                                                            testResult = errMsg
                                                            snackbarHostState.showSnackbar(errMsg)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp)
                                            ) { Text(strings.settingsSyncTestConnection, style = MaterialTheme.typography.labelMedium) }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        isSyncing = true
                                                        syncStatus = strings.settingsSyncing
                                                        try {
                                                            val result = withContext(Dispatchers.IO) {
                                                                val localDecks = deckRepository.getAll().first()
                                                                val localCards = cardRepository.getAll().first()
                                                                val json = exportManager.exportToJson(localDecks, localCards)
                                                                syncManager.upload(config, json).getOrThrow()
                                                                val remoteResult = syncManager.download(config)
                                                                var importedDecks = 0
                                                                var importedCards = 0
                                                                if (remoteResult.isSuccess) {
                                                                    val remote = exportManager.importFromJson(remoteResult.getOrThrow())
                                                                    if (remote != null) {
                                                                        val localDeckIds = localDecks.map { it.id }.toSet()
                                                                        for (deck in remote.decks) {
                                                                            if (deck.id !in localDeckIds) {
                                                                                val now = Clock.System.now()
                                                                                deckRepository.insert(Deck(
                                                                                    id = deck.id, knowledgeBaseId = "default",
                                                                                    name = deck.name, description = deck.description ?: "",
                                                                                    color = deck.color, icon = deck.icon,
                                                                                    createdAt = now, updatedAt = now
                                                                                ))
                                                                                importedDecks++
                                                                            }
                                                                        }
                                                                        val localCardIds = localCards.map { it.id }.toSet()
                                                                        for (card in remote.cards) {
                                                                            if (card.id !in localCardIds) {
                                                                                cardRepository.insert(Card(
                                                                                    id = card.id, deckId = card.deckId,
                                                                                    type = CardType.valueOf(card.type),
                                                                                    front = card.front, back = card.back,
                                                                                    tags = card.tags,
                                                                                    createdAt = Instant.parse(card.createdAt),
                                                                                    updatedAt = Instant.parse(card.updatedAt)
                                                                                ))
                                                                                importedCards++
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                webDavConfigManager.updateLastSync(config.id)
                                                                localDecks.size
                                                            }
                                                            syncStatus = strings.settingsSyncSuccess(result)
                                                            snackbarHostState.showSnackbar(strings.settingsSyncSuccess(result))
                                                            reloadConfigs()
                                                        } catch (e: Exception) {
                                                            syncStatus = strings.settingsSyncError(e.message ?: "Unknown")
                                                            snackbarHostState.showSnackbar(strings.settingsSyncError(e.message ?: "Unknown"))
                                                        } finally { isSyncing = false }
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp),
                                                enabled = !isSyncing
                                            ) { Text(strings.actionSync, style = MaterialTheme.typography.labelMedium) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isEditing) {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val config = WebDavConfig(
                                        id = editConfig?.id ?: Clock.System.now().toEpochMilliseconds().toString(),
                                        name = editName.ifBlank { editUrl },
                                        url = editUrl,
                                        username = editUser,
                                        password = editPass,
                                        isDefault = editConfig?.isDefault ?: dialogConfigs.isEmpty()
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
                        }) { Text(strings.actionSave) }
                    } else {
                        TextButton(onClick = {
                            editConfig = null
                            editName = ""
                            editUrl = ""
                            editUser = ""
                            editPass = ""
                            testResult = null
                            isEditing = true
                        }) { Text(strings.settingsSyncAddConfig) }
                    }
                },
                dismissButton = {
                    if (isEditing) {
                        TextButton(onClick = {
                            isEditing = false
                            editConfig = null
                            testResult = null
                        }) { Text(strings.actionCancel) }
                    } else {
                        TextButton(onClick = { showSyncDialog = false }) { Text(strings.actionCancel) }
                    }
                }
            )
        }
    }
}

@Composable
private fun answerDisplayName(mode: AnswerDisplayMode): String {
    val s = koinInject<I18nManager>().strings
    return when (mode) {
        AnswerDisplayMode.FLIP -> s.displayFlip
        AnswerDisplayMode.SPLIT -> s.displaySplit
    }
}

@Composable
private fun answerDisplayDesc(mode: AnswerDisplayMode): String {
    val s = koinInject<I18nManager>().strings
    return when (mode) {
        AnswerDisplayMode.FLIP -> s.displayFlipDesc
        AnswerDisplayMode.SPLIT -> s.displaySplitDesc
    }
}

@Composable
private fun reviewModeName(mode: ReviewMode): String {
    val s = koinInject<I18nManager>().strings
    return when (mode) {
        ReviewMode.FSRS -> s.algoFsrs
        ReviewMode.SM2 -> s.algoSm2
        ReviewMode.LEITNER -> s.algoLeitner
        ReviewMode.SIMPLE -> s.algoSimple
    }
}

@Composable
private fun reviewModeDesc(mode: ReviewMode): String {
    val s = koinInject<I18nManager>().strings
    return when (mode) {
        ReviewMode.FSRS -> s.algoFsrsDesc
        ReviewMode.SM2 -> s.algoSm2Desc
        ReviewMode.LEITNER -> s.algoLeitnerDesc
        ReviewMode.SIMPLE -> s.algoSimpleDesc
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(content = content)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}






