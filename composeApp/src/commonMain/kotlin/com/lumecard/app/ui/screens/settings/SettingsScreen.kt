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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.shared.AppVersion
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.data.UpdateManager
import com.lumecard.shared.data.UpdateState
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.app.platform.copyToClipboard
import com.lumecard.shared.domain.scheduler.ReviewMode
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.app.i18n.AppLocale
import com.lumecard.app.ui.components.LumeCardDialog
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.components.LumeCardTextField
import com.lumecard.app.ui.components.UpdateCheckDialog
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lumecard.app.platform.getAppVersion
import com.lumecard.app.platform.pickSaveFile
import com.lumecard.app.platform.pickOpenFile
import com.lumecard.app.platform.readFileContent
import com.lumecard.app.platform.writeFileContent
import org.koin.compose.koinInject

class SettingsScreen : Screen {
    override val key: ScreenKey = "Settings"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val settingsViewModel: SettingsViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val i18nManager = koinInject<I18nManager>()
        val settingsState = settingsViewModel.state
        val exportManager: ExportManager = koinInject()
        val deckRepository: DeckRepository = koinInject()
        val cardRepository: CardRepository = koinInject()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val webDavConfigManager: WebDavConfigManager = koinInject()
        val knowledgeBaseRepository: com.lumecard.shared.repository.KnowledgeBaseRepository = koinInject()
        val updateManager: UpdateManager = koinInject()
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius

        var defaultConfigName by remember { mutableStateOf("") }
        var showModeDropdown by remember { mutableStateOf(false) }
        var showAnswerModeDropdown by remember { mutableStateOf(false) }
        var showGoalDialog by remember { mutableStateOf(false) }
        var goalInput by remember { mutableStateOf("") }
        var showUpdateDialog by remember { mutableStateOf(false) }
        var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

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
                    .padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.section),
            ) {
                Spacer(modifier = Modifier.height(spacing.xs))

                // === Learning ===
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.settingsLearning,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Column {
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
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                        HorizontalDivider()
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
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                    FilledIconButton(onClick = {
                                        val v = (settingsState.newCardsPerDay + 5).coerceAtMost(999)
                                        settingsViewModel.setNewCardsPerDay(v)
                                    }, modifier = Modifier.size(32.dp)) {
                                        Text("+", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.settingsReviewMode) },
                            supportingContent = { Text(reviewModeDesc(settingsState.reviewMode)) },
                            trailingContent = {
                                Box {
                                    TextButton(onClick = { showModeDropdown = true }) {
                                        Text(
                                            reviewModeName(settingsState.reviewMode),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = showModeDropdown,
                                        onDismissRequest = { showModeDropdown = false },
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
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                // === Answer Display ===
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.settingsAnswerDisplay,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.settingsAnswerMode) },
                        supportingContent = { Text(answerDisplayDesc(settingsState.answerDisplayMode)) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showAnswerModeDropdown = true }) {
                                    Text(
                                        answerDisplayName(settingsState.answerDisplayMode),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = showAnswerModeDropdown,
                                    onDismissRequest = { showAnswerModeDropdown = false },
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
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                // === Appearance ===
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.settingsAppearance,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.settingsDarkMode) },
                        supportingContent = { Text(strings.settingsDarkModeDesc) },
                        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settingsState.isDarkMode,
                                onCheckedChange = { settingsViewModel.setDarkMode(it) },
                            )
                        },
                    )
                }

                // === Language ===
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.settingsLanguage,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    var showLangDropdown by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(strings.settingsLanguageDesc) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showLangDropdown = true }) {
                                    Text(
                                        when (settingsState.language) {
                                            AppLocale.SYSTEM -> i18nManager.systemStrings.langSystem
                                            AppLocale.ZH_CN -> "🇨🇳 ${strings.langZhCn}"
                                            AppLocale.ZH_TW -> "🇹🇼 ${strings.langZhTw}"
                                            AppLocale.EN -> "🇺🇸 ${strings.langEn}"
                                            AppLocale.JA -> "🇯🇵 ${strings.langJa}"
                                            AppLocale.ES -> "🇪🇸 ${strings.langEs}"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = showLangDropdown,
                                    onDismissRequest = { showLangDropdown = false },
                                ) {
                                    AppLocale.entries.forEach { locale ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (locale) {
                                                        AppLocale.SYSTEM -> "\u2699\uFE0F ${i18nManager.systemStrings.langSystem}"
                                                        AppLocale.ZH_CN -> "🇨🇳 ${strings.langZhCn}"
                                                        AppLocale.ZH_TW -> "🇹🇼 ${strings.langZhTw}"
                                                        AppLocale.EN -> "🇺🇸 ${strings.langEn}"
                                                        AppLocale.JA -> "🇯🇵 ${strings.langJa}"
                                                        AppLocale.ES -> "🇪🇸 ${strings.langEs}"
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
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                // === Notifications ===
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.settingsNotifications,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.settingsDailyReminder) },
                        supportingContent = { Text(strings.settingsDailyReminderDesc) },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settingsState.notificationsEnabled,
                                onCheckedChange = { settingsViewModel.setNotifications(it) },
                            )
                        },
                    )
                }

                // === Data Management ===
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.settingsDataManagement,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(strings.settingsExport) },
                            supportingContent = { Text(strings.settingsExportDesc) },
                            leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    try {
                                        val filePath = pickSaveFile("lumecard_export.json", "application/json")
                                        if (filePath == null) {
                                            snackbarHostState.showSnackbar(strings.actionCancel)
                                            return@launch
                                        }
                                        val knowledgeBases = knowledgeBaseRepository.getAll().first()
                                        val decks = deckRepository.getAll().first()
                                        val allCards = cardRepository.getAll().first()
                                        val json = exportManager.exportData(knowledgeBases, decks, allCards)
                                        val success = writeFileContent(filePath, json)
                                        if (success) {
                                            snackbarHostState.showSnackbar(strings.settingsExportSuccess(json.length))
                                        } else {
                                            snackbarHostState.showSnackbar(strings.settingsExportError(strings.exportErrorWrite))
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(strings.settingsExportError(e.message ?: "未知错误"))
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.settingsImport) },
                            supportingContent = { Text(strings.settingsImportDesc) },
                            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    try {
                                        val filePath = pickOpenFile("application/json")
                                        if (filePath == null) {
                                            snackbarHostState.showSnackbar(strings.settingsImportHint)
                                            return@launch
                                        }
                                        val json = readFileContent(filePath)
                                        if (json == null) {
                                            snackbarHostState.showSnackbar(strings.settingsImportError("Cannot read file"))
                                            return@launch
                                        }
                                        val export = exportManager.importData(json)
                                        if (export == null) {
                                            snackbarHostState.showSnackbar(strings.settingsImportError("Invalid JSON format"))
                                            return@launch
                                        }
                                        val importedKBs = export.knowledgeBases.size
                                        val importedDecks = export.decks.size
                                        val importedCards = export.cards.size
                                        snackbarHostState.showSnackbar("Imported: $importedKBs KBs, $importedDecks decks, $importedCards cards")
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(strings.settingsImportError(e.message ?: "Unknown"))
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.settingsCloudSync) },
                            supportingContent = {
                                Text(
                                    if (defaultConfigName.isNotEmpty()) "$defaultConfigName | ${strings.settingsCloudSyncDesc}"
                                    else strings.settingsCloudSyncDesc,
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            modifier = Modifier.clickable { navigator.push(WebDavConfigScreen()) },
                            trailingContent = {
                                TextButton(onClick = { navigator.push(WebDavConfigScreen()) }) {
                                    Text(
                                        if (defaultConfigName.isNotEmpty()) defaultConfigName
                                        else strings.settingsSyncNotConfigured,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }

                // === About ===
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        strings.settingsAbout,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(spacing.lg))
                        Surface(
                            shape = radius.pill,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("LC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(spacing.sm))
                        Text("LumeCard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("v${getAppVersion()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(spacing.md))
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.settingsCheckUpdate) },
                            supportingContent = { Text("v${getAppVersion()}") },
                            leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            modifier = Modifier.clickable {
                                updateState = UpdateState.Checking
                                showUpdateDialog = true
                                scope.launch {
                                    val info = updateManager.checkForUpdate()
                                    updateState = if (info?.hasUpdate == true) {
                                        UpdateState.UpdateAvailable(info)
                                    } else if (info != null) {
                                        UpdateState.UpToDate
                                    } else {
                                        UpdateState.Error(strings.updateError)
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.settingsDeveloper) },
                            trailingContent = { Text("Brucegot9stars", style = MaterialTheme.typography.bodyMedium) },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.settingsLicense) },
                            trailingContent = { Text(strings.settingsOpenSource, style = MaterialTheme.typography.bodyMedium) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xxl))
            }
        }

        if (showGoalDialog) {
            LumeCardDialog(
                title = strings.settingsGoalDialogTitle,
                onDismiss = { showGoalDialog = false },
                onConfirm = {
                    goalInput.toIntOrNull()?.let { settingsViewModel.setDailyGoal(it) }
                    showGoalDialog = false
                },
                confirmText = strings.actionOk,
                confirmEnabled = goalInput.toIntOrNull() != null,
            ) {
                LumeCardTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it.filter { c -> c.isDigit() } },
                    label = strings.settingsCardCount,
                )
            }
        }

        if (showUpdateDialog) {
            UpdateCheckDialog(
                updateState = updateState,
                onDismiss = { showUpdateDialog = false },
                onCheckUpdate = {
                    updateState = UpdateState.Checking
                    scope.launch {
                        val info = updateManager.checkForUpdate()
                        updateState = if (info?.hasUpdate == true) {
                            UpdateState.UpdateAvailable(info)
                        } else if (info != null) {
                            UpdateState.UpToDate
                        } else {
                            UpdateState.Error(strings.updateError)
                        }
                    }
                },
                onUpdate = {
                    val info = (updateState as? UpdateState.UpdateAvailable)?.info ?: return@UpdateCheckDialog
                    scope.launch {
                        updateState = UpdateState.Downloading(0f)
                        try {
                            val downloadUrl = "https://github.com/Brucegot9stars/LumeCardWithOpenCode/releases/download/v${info.version}/composeApp-release.apk"
                            val destFile = java.io.File(java.io.File(System.getProperty("java.io.tmpdir") ?: System.getProperty("user.home") ?: "."), "LumeCard-v${info.version}.apk")
                            val success = updateManager.downloadApk(downloadUrl, destFile) { progress ->
                                updateState = UpdateState.Downloading(progress)
                            }
                            if (success) {
                                updateState = UpdateState.Installing
                                kotlinx.coroutines.delay(1000)
                                updateState = UpdateState.Complete
                            } else {
                                updateState = UpdateState.Error("下载失败，请检查网络连接或稍后重试")
                            }
                        } catch (e: Exception) {
                            updateState = UpdateState.Error("更新失败：${e.message ?: "未知错误"}")
                        }
                    }
                },
                onCopyError = { errorMsg ->
                    scope.launch {
                        try {
                            copyToClipboard(errorMsg)
                            snackbarHostState.showSnackbar(strings.updateCopySuccess)
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(strings.updateCopyError)
                        }
                    }
                },
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

