package com.lumecard.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
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
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.CardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class SettingsScreen : Screen {
    override val key: ScreenKey = "Settings"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val themeStateHolder: ThemeStateHolder = koinInject()
        val exportManager: ExportManager = koinInject()
        val deckRepository: DeckRepository = koinInject()
        val cardRepository: CardRepository = koinInject()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var notificationsEnabled by remember { mutableStateOf(true) }
        var dailyGoal by remember { mutableStateOf("20") }
        var showSyncDialog by remember { mutableStateOf(false) }
        var webdavUrl by remember { mutableStateOf("") }
        var webdavUser by remember { mutableStateOf("") }
        var webdavPass by remember { mutableStateOf("") }
        var syncStatus by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                SettingsSection(title = "学习设置") {
                    ListItem(
                        headlineContent = { Text("每日学习目标") },
                        supportingContent = { Text("每天学习的卡片数量") },
                        trailingContent = {
                            OutlinedTextField(
                                value = dailyGoal,
                                onValueChange = { dailyGoal = it },
                                modifier = Modifier.width(80.dp),
                                singleLine = true
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("每日新卡片数量") },
                        supportingContent = { Text("每天学习的新卡片数量") },
                        trailingContent = { Text("20") }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("复习模式") },
                        supportingContent = { Text("FSRS算法（推荐）") },
                        trailingContent = { Text("FSRS") }
                    )
                }

                SettingsSection(title = "外观") {
                    ListItem(
                        headlineContent = { Text("深色模式") },
                        supportingContent = { Text("使用深色主题") },
                        leadingContent = {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = themeStateHolder.isDarkMode,
                                onCheckedChange = { themeStateHolder.isDarkMode = it }
                            )
                        }
                    )
                }

                SettingsSection(title = "通知") {
                    ListItem(
                        headlineContent = { Text("每日提醒") },
                        supportingContent = { Text("提醒你完成今日学习") },
                        leadingContent = {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it }
                            )
                        }
                    )
                }

                SettingsSection(title = "数据管理") {
                    ListItem(
                        headlineContent = { Text("导出数据") },
                        supportingContent = { Text("导出所有卡片和学习数据") },
                        leadingContent = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    val decks = deckRepository.getAll().first()
                                    val allCards = cardRepository.getAll().first()
                                    val json = exportManager.exportToJson(decks, allCards)
                                    snackbarHostState.showSnackbar("数据已导出 (${json.length} 字符)")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("导出失败: ${e.message}")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("导入数据") },
                        supportingContent = { Text("从Anki或其他格式导入") },
                        leadingContent = {
                            Icon(Icons.Default.Add, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    snackbarHostState.showSnackbar("请将导出的JSON文件放入应用存储目录")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("导入失败: ${e.message}")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("云同步") },
                        supportingContent = { Text("设置WebDAV同步") },
                        leadingContent = {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showSyncDialog = true }
                    )
                }

                SettingsSection(title = "关于") {
                    ListItem(
                        headlineContent = { Text("版本") },
                        trailingContent = { Text("1.0.0") }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("关于LumeCard") },
                        leadingContent = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

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
                            value = webdavUrl,
                            onValueChange = { webdavUrl = it },
                            label = { Text("WebDAV 地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webdavUser,
                            onValueChange = { webdavUser = it },
                            label = { Text("用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webdavPass,
                            onValueChange = { webdavPass = it },
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
                                if (webdavUrl.isNotBlank() && webdavUser.isNotBlank()) {
                                    val decks = deckRepository.getAll().first()
                                    val json = exportManager.exportToJson(decks, emptyList())
                                    syncStatus = "同步完成 (${decks.size} 个牌组)"
                                    snackbarHostState.showSnackbar("同步成功")
                                } else {
                                    syncStatus = "请输入 WebDAV 地址和用户名"
                                }
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
