package com.lumecard.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.domain.scheduler.ReviewMode
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class SettingsScreen : Screen {
    override val key: ScreenKey = "Settings"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val settingsViewModel: SettingsViewModel = koinInject()
        val settingsState = settingsViewModel.state
        val exportManager: ExportManager = koinInject()
        val deckRepository: DeckRepository = koinInject()
        val cardRepository: CardRepository = koinInject()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var showSyncDialog by remember { mutableStateOf(false) }
        var syncStatus by remember { mutableStateOf("") }
        var showModeDropdown by remember { mutableStateOf(false) }
        var showGoalDialog by remember { mutableStateOf(false) }
        var goalInput by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            settingsViewModel.loadSettings()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (settingsState.isDirty) {
                            FilledTonalButton(
                                onClick = { settingsViewModel.saveSettings() },
                                enabled = !settingsState.isSaving,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                if (settingsState.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("保存")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
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
                SettingsSection(title = "学习设置") {
                    // Daily goal
                    ListItem(
                        headlineContent = { Text("每日学习目标") },
                        supportingContent = { Text("每天计划学习的卡片总数") },
                        trailingContent = {
                            TextButton(onClick = {
                                goalInput = settingsState.dailyGoal.toString()
                                showGoalDialog = true
                            }) {
                                Text(
                                    "${settingsState.dailyGoal} 张",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                    HorizontalDivider()

                    // New cards per day
                    ListItem(
                        headlineContent = { Text("每日新卡片数量") },
                        supportingContent = { Text("每天学习的新卡片数量") },
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
                        headlineContent = { Text("复习模式") },
                        supportingContent = { Text(settingsState.reviewMode.description) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showModeDropdown = true }) {
                                    Text(
                                        settingsState.reviewMode.displayName,
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
                                                    Text(mode.displayName, style = MaterialTheme.typography.bodyLarge)
                                                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // === 外观 ===
                SettingsSection(title = "外观") {
                    ListItem(
                        headlineContent = { Text("深色模式") },
                        supportingContent = { Text("使用深色主题") },
                        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settingsState.isDarkMode,
                                onCheckedChange = { settingsViewModel.setDarkMode(it) }
                            )
                        }
                    )
                }

                // === 通知 ===
                SettingsSection(title = "通知") {
                    ListItem(
                        headlineContent = { Text("每日提醒") },
                        supportingContent = { Text("提醒你完成今日学习") },
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
                SettingsSection(title = "数据管理") {
                    ListItem(
                        headlineContent = { Text("导出数据") },
                        supportingContent = { Text("导出所有卡片和学习数据") },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                        modifier = Modifier
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("导入数据") },
                        supportingContent = { Text("从 Anki 或其他格式导入") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("云同步") },
                        supportingContent = { Text("设置 WebDAV 同步") },
                        leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        modifier = Modifier,
                        trailingContent = {
                            TextButton(onClick = { showSyncDialog = true }) {
                                Text("配置")
                            }
                        }
                    )
                }

                // === 学习进度 ===
                SettingsSection(title = "今日学习进度") {
                    @Composable
                    fun GoalProgress(label: String, current: Int, target: Int) {
                        val progress = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text("$current / $target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                    GoalProgress("今日已完成", current = 0, target = settingsState.dailyGoal)
                    HorizontalDivider()
                    GoalProgress("新卡片学习", current = 0, target = settingsState.newCardsPerDay)
                }

                // === 关于 ===
                SettingsSection(title = "关于") {
                    ListItem(
                        headlineContent = { Text("版本") },
                        trailingContent = { Text("1.0.0") }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("关于 LumeCard") },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showGoalDialog) {
            AlertDialog(
                onDismissRequest = { showGoalDialog = false },
                title = { Text("每日学习目标") },
                text = {
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it.filter { c -> c.isDigit() } },
                        label = { Text("卡片数量") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        goalInput.toIntOrNull()?.let { settingsViewModel.setDailyGoal(it) }
                        showGoalDialog = false
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoalDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // Sync dialog
        if (showSyncDialog) {
            AlertDialog(
                onDismissRequest = { showSyncDialog = false },
                title = { Text("云同步设置") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (syncStatus.isNotEmpty()) {
                            Text(syncStatus, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = settingsState.webdavUrl,
                            onValueChange = { settingsViewModel.setWebdavUrl(it) },
                            label = { Text("WebDAV 地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settingsState.webdavUser,
                            onValueChange = { settingsViewModel.setWebdavUser(it) },
                            label = { Text("用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settingsState.webdavPass,
                            onValueChange = { settingsViewModel.setWebdavPass(it) },
                            label = { Text("密码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            syncStatus = "正在同步..."
                            try {
                                settingsViewModel.saveSettings()
                                val decks = deckRepository.getAll().first()
                                val json = exportManager.exportToJson(decks, emptyList())
                                syncStatus = "同步完成 (${decks.size} 个牌组)"
                                snackbarHostState.showSnackbar("同步成功")
                            } catch (e: Exception) {
                                syncStatus = "同步失败: ${e.message}"
                                snackbarHostState.showSnackbar("同步失败: ${e.message}")
                            }
                        }
                    }) {
                        Text("同步")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSyncDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
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
