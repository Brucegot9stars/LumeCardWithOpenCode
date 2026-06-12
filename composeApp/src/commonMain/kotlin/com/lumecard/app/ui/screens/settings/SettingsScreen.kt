package com.lumecard.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class SettingsScreen : Screen {
    override val key: ScreenKey = "Settings"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var isDarkMode by remember { mutableStateOf(false) }
        var notificationsEnabled by remember { mutableStateOf(true) }
        var dailyGoal by remember { mutableStateOf("20") }

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
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 学习设置
                SettingsSection(title = "学习设置") {
                    // 每日目标
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

                    // 新卡片设置
                    ListItem(
                        headlineContent = { Text("每日新卡片数量") },
                        supportingContent = { Text("每天学习的新卡片数量") },
                        trailingContent = {
                            Text("20")
                        }
                    )
                    HorizontalDivider()

                    // 复习设置
                    ListItem(
                        headlineContent = { Text("复习模式") },
                        supportingContent = { Text("FSRS算法（推荐）") },
                        trailingContent = {
                            Text("FSRS")
                        }
                    )
                }

                // 外观设置
                SettingsSection(title = "外观") {
                    ListItem(
                        headlineContent = { Text("深色模式") },
                        supportingContent = { Text("使用深色主题") },
                        leadingContent = {
                            Icon(Icons.Default.DarkMode, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { isDarkMode = it }
                            )
                        }
                    )
                }

                // 通知设置
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

                // 数据管理
                SettingsSection(title = "数据管理") {
                    ListItem(
                        headlineContent = { Text("导出数据") },
                        supportingContent = { Text("导出所有卡片和学习数据") },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null)
                        }
                    )
                    HorizontalDivider()

                    ListItem(
                        headlineContent = { Text("导入数据") },
                        supportingContent = { Text("从Anki或其他格式导入") }
                    )
                    HorizontalDivider()

                    ListItem(
                        headlineContent = { Text("云同步") },
                        supportingContent = { Text("设置WebDAV同步") }
                    )
                }

                // 关于
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
