package com.lumecard.app.ui.screens.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import org.koin.compose.koinInject

class CreateCardScreen(
    private val deckId: String,
    private val deckName: String,
    private val editCard: Card? = null
) : Screen {
    override val key: ScreenKey = "CreateCard_${editCard?.id ?: deckId}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: CardViewModel = koinInject()
        var front by remember { mutableStateOf(editCard?.front ?: "") }
        var back by remember { mutableStateOf(editCard?.back ?: "") }
        var cardType by remember { mutableStateOf(editCard?.type ?: CardType.BASIC) }
        var tags by remember { mutableStateOf(editCard?.tags?.joinToString(", ") ?: "") }
        var showTypeMenu by remember { mutableStateOf(false) }
        val isEditing = editCard != null

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) "编辑卡片" else "创建卡片") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (isEditing && editCard != null) {
                                    viewModel.updateCard(
                                        card = editCard,
                                        front = front,
                                        back = back,
                                        type = cardType,
                                        tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    )
                                } else {
                                    viewModel.createCard(
                                        deckId = deckId,
                                        front = front,
                                        back = back,
                                        type = cardType,
                                        tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    )
                                }
                                navigator.pop()
                            },
                            enabled = front.isNotBlank() && back.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "保存")
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        "牌组: $deckName",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = showTypeMenu,
                    onExpandedChange = { showTypeMenu = it }
                ) {
                    OutlinedTextField(
                        value = cardType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("卡片类型") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeMenu)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        CardType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    cardType = type
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }

                Text(
                    "正面（问题）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    placeholder = { Text("输入卡片正面内容...") },
                    supportingText = { Text("支持Markdown格式") }
                )

                Text(
                    "背面（答案）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    placeholder = { Text("输入卡片背面内容...") },
                    supportingText = { Text("支持Markdown格式") }
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标签") },
                    placeholder = { Text("用逗号分隔多个标签") },
                    supportingText = { Text("例如: 英语, 单词, 核心词汇") }
                )

                if (front.isNotBlank() || back.isNotBlank()) {
                    Text(
                        "预览",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (front.isNotBlank()) {
                                Text(
                                    "问题:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(front, style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (back.isNotBlank()) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "答案:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(back, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
