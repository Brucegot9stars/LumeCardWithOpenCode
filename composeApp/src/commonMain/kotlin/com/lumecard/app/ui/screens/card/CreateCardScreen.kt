package com.lumecard.app.ui.screens.card

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.components.MarkdownText
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
        var showTypeHelp by remember { mutableStateOf(true) }
        val isEditing = editCard != null

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) "编辑卡片" else "创建卡片") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (isEditing) {
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

                // 卡片类型选择
                ExposedDropdownMenuBox(
                    expanded = showTypeMenu,
                    onExpandedChange = { showTypeMenu = it }
                ) {
                    OutlinedTextField(
                        value = cardTypeLabel(cardType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("卡片类型") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                                text = {
                                    Column {
                                        Text(cardTypeLabel(type), style = MaterialTheme.typography.bodyLarge)
                                        Text(cardTypeDesc(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    cardType = type
                                    showTypeMenu = false
                                },
                                leadingIcon = {
                                    if (type == cardType) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }
                }

                // 类型专用输入区
                CardTypeInput(
                    type = cardType,
                    front = front,
                    onFrontChange = { front = it },
                    back = back,
                    onBackChange = { back = it }
                )

                // 标签
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标签") },
                    placeholder = { Text("用逗号分隔多个标签") },
                    supportingText = { Text("例如: 英语, 单词, 核心词汇") }
                )

                // 预览
                if (front.isNotBlank() || back.isNotBlank()) {
                    Text("预览", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("问题:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MarkdownText(markdown = front, modifier = Modifier.fillMaxWidth())
                            if (back.isNotBlank()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text("答案:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MarkdownText(markdown = back, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                // 类型说明
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("卡片类型使用说明", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { showTypeHelp = !showTypeHelp }) {
                                Icon(
                                                    if (showTypeHelp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showTypeHelp) "收起" else "展开"
                                )
                            }
                        }
                        AnimatedVisibility(visible = showTypeHelp) {
                            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                Text(typeHelpText(cardType), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Text("示例：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(typeExampleText(cardType), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CardTypeInput(
    type: CardType,
    front: String,
    onFrontChange: (String) -> Unit,
    back: String,
    onBackChange: (String) -> Unit
) {
    when (type) {
        CardType.BASIC, CardType.REVERSED, CardType.MARKDOWN, CardType.AI_GENERATED -> {
            BasicCardFields(
                front = front, onFrontChange = onFrontChange,
                back = back, onBackChange = onBackChange,
                frontLabel = if (type == CardType.REVERSED) "正面（背面将先显示）" else "正面（问题）",
                backLabel = if (type == CardType.REVERSED) "背面（正面将先显示）" else "背面（答案）",
                frontPlaceholder = "输入卡片正面内容...",
                backPlaceholder = "输入卡片背面内容..."
            )
        }
        CardType.CLOZE -> {
            Text("填空内容", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("使用 {{c1::答案}} 格式标记填空位置，数字表示挖空编号", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text("例如: Apple was founded by {{c1::Steve Jobs}}.") }
            )
            Text("完整文本", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("输入完整文本（显示答案时展示）") }
            )
        }
        CardType.MULTIPLE_CHOICE -> {
            Text("题目", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                placeholder = { Text("输入题目...") }
            )
            Text("选项与答案", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("每行一个选项，正确选项前加 ✓ 或 √", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text("例如:\n✓ Python\nJava\nC++\nJavaScript") }
            )
        }
        CardType.IMAGE_OCCLUSION -> {
            Text("图片引用", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("图片路径或 URL") },
                supportingText = { Text("支持本地路径或网络图片链接") }
            )
            Text("说明内容", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("输入图片相关说明...") }
            )
        }
        CardType.AUDIO -> {
            Text("音频引用", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("音频文件路径或 URL") },
                supportingText = { Text("支持 MP3、WAV 等格式") }
            )
            Text("文字内容", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("输入音频对应的文字内容...") }
            )
        }
        CardType.VIDEO -> {
            Text("视频引用", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("视频文件路径或 URL") },
                supportingText = { Text("支持 MP4 等格式") }
            )
            Text("文字内容", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("输入视频对应的文字内容...") }
            )
        }
    }
}

@Composable
private fun BasicCardFields(
    front: String, onFrontChange: (String) -> Unit,
    back: String, onBackChange: (String) -> Unit,
    frontLabel: String, backLabel: String,
    frontPlaceholder: String, backPlaceholder: String
) {
    Text(frontLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = front,
        onValueChange = onFrontChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
        placeholder = { Text(frontPlaceholder) },
        supportingText = { Text("支持 Markdown 格式") }
    )
    Text(backLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = back,
        onValueChange = onBackChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
        placeholder = { Text(backPlaceholder) },
        supportingText = { Text("支持 Markdown 格式") }
    )
}

private fun cardTypeLabel(type: CardType): String = when (type) {
    CardType.BASIC -> "基础问答卡"
    CardType.REVERSED -> "双向问答卡"
    CardType.CLOZE -> "填空题卡"
    CardType.MULTIPLE_CHOICE -> "选择题卡"
    CardType.IMAGE_OCCLUSION -> "图片记忆卡"
    CardType.AUDIO -> "音频学习卡"
    CardType.VIDEO -> "视频学习卡"
    CardType.MARKDOWN -> "Markdown 卡片"
    CardType.AI_GENERATED -> "AI 生成卡片"
}

private fun cardTypeDesc(type: CardType): String = when (type) {
    CardType.BASIC -> "标准问答，正面问题，背面答案"
    CardType.REVERSED -> "正反面互换，两面都能学习"
    CardType.CLOZE -> "挖空填空，适合单词和定义记忆"
    CardType.MULTIPLE_CHOICE -> "选择题，从多个选项中选择正确答案"
    CardType.IMAGE_OCCLUSION -> "通过图片遮挡辅助记忆"
    CardType.AUDIO -> "听力练习，音频+文字对照"
    CardType.VIDEO -> "视频学习，视频+文字对照"
    CardType.MARKDOWN -> "支持 Markdown 格式渲染"
    CardType.AI_GENERATED -> "由 AI 自动生成的卡片"
}

private fun typeHelpText(type: CardType): String = when (type) {
    CardType.BASIC -> "基础问答卡是最常用的卡片类型。正面写问题或提示，背面写答案。适用于词汇记忆、知识点复习、定义背诵等场景。"
    CardType.REVERSED -> "双向问答卡在基础问答卡的基础上增加反向卡片。学习时会同时看到正面→背面和背面→正面两种方向，加深记忆。"
    CardType.CLOZE -> "填空题卡通过挖空关键信息来促进主动回忆。使用 {{c1::答案}} 格式标记填空位置，数字表示挖空编号，可在同一文本中设置多个填空。"
    CardType.MULTIPLE_CHOICE -> "选择题卡提供多个选项供选择。在答案区域每行写一个选项，正确选项前加 ✓ 前缀标记。学习时正面显示题目，背面显示所有选项并高亮正确答案。"
    CardType.IMAGE_OCCLUSION -> "图片记忆卡通过图片遮挡方式辅助记忆。正面填写图片路径或 URL，背面填写图片相关的说明内容。适用于地图、图表、解剖图等视觉学习。"
    CardType.AUDIO -> "音频学习卡适用于听力练习和发音学习。正面填写音频文件路径，背面填写对应的文字内容。学习时可播放音频进行听写训练。"
    CardType.VIDEO -> "视频学习卡适用于视频内容的学习。正面填写视频文件路径，背面填写对应的文字摘要或讲解内容。支持视频学习场景。"
    CardType.MARKDOWN -> "Markdown 卡片支持使用 Markdown 语法格式化内容，包括标题、列表、代码块、表格、加粗、斜体等。适合技术文档和代码学习。"
    CardType.AI_GENERATED -> "AI 生成卡片由人工智能自动生成。正面和背面内容均可由 AI 根据指定主题自动创建，适合快速生成大规模学习材料。"
}

private fun typeExampleText(type: CardType): String = when (type) {
    CardType.BASIC -> "Q: What is Apple?\nA: A technology company."
    CardType.REVERSED -> "正面: A technology company.\n背面: What is Apple?"
    CardType.CLOZE -> "中国首都是 {{c1::北京}}，人口约 {{c2::2154}} 万。"
    CardType.MULTIPLE_CHOICE -> "Which are programming languages?\n✓ Python\n✓ Java\nApple\n✓ JavaScript"
    CardType.IMAGE_OCCLUSION -> "正面: https://example.com/diagram.png\n背面: 人体消化系统结构图"
    CardType.AUDIO -> "正面: /audio/lesson1.mp3\n背面: Hello, how are you? 你好吗？"
    CardType.VIDEO -> "正面: /video/tutorial.mp4\n背面: 本视频讲解了牛顿第二定律"
    CardType.MARKDOWN -> "正面: # **标题** 和 `代码`\n背面: ## 列表项\n- item1\n- item2"
    CardType.AI_GENERATED -> "正面: 由 AI 自动生成的问题\n背面: 由 AI 自动生成的答案"
}
