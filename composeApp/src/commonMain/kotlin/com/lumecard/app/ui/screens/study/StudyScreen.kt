package com.lumecard.app.ui.screens.study

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.components.MarkdownText
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Rating
import org.koin.compose.koinInject

class StudyScreen(
    private val deckId: String,
    private val deckName: String
) : Screen {
    override val key: ScreenKey = "Study_$deckId"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: StudyViewModel = koinInject()
        val cards by viewModel.cards.collectAsState()
        val currentIndex by viewModel.currentCardIndex.collectAsState()
        val isFlipped by viewModel.isFlipped.collectAsState()
        val completedCards by viewModel.completedCards.collectAsState()
        val swipeFeedback by viewModel.swipeFeedback.collectAsState()

        val currentCard = cards.getOrNull(currentIndex)
        var swipeOffset by remember { mutableStateOf(0f) }
        val screenWidthDp = remember { mutableStateOf(360f) }

        LaunchedEffect(deckId) {
            viewModel.loadCards(deckId)
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val maxWidth = maxWidth.value * 0.3f // 30% threshold
            LaunchedEffect(maxWidth) { screenWidthDp.value = maxWidth }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("学习: $deckName") },
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
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 进度指示器
                    if (cards.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { (currentIndex.toFloat() / cards.size).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "进度: ${if (currentIndex < cards.size) currentIndex + 1 else cards.size} / ${cards.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 卡片展示 (带滑动手势)
                    currentCard?.let { card ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            val threshold = screenWidthDp.value
                                            if (swipeOffset > threshold) {
                                                // 右滑 → 上一张
                                                if (viewModel.canGoBack) {
                                                    viewModel.goBack()
                                                    viewModel.showSwipeFeedback("← 返回上一张")
                                                }
                                             } else if (swipeOffset < -threshold) {
                                                // 左滑 → 简单 + 下一张（不依赖翻转状态）
                                                viewModel.rateCard(Rating.EASY)
                                                viewModel.showSwipeFeedback("✓ 简单")
                                            }
                                            swipeOffset = 0f
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            val newOffset = (swipeOffset + dragAmount).coerceIn(-maxWidth * 2, maxWidth * 2)
                                            swipeOffset = newOffset
                                        }
                                    )
                                }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(x = (swipeOffset / 3).dp),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isFlipped) 4.dp else 8.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFlipped)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp)
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CardContent(
                                        card = card,
                                        isFlipped = isFlipped
                                    )
                                }
                            }

                            // 滑动反馈
                            if (swipeFeedback != null) {
                                Surface(
                                    modifier = Modifier.align(Alignment.Center),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f)
                                ) {
                                    Text(
                                        swipeFeedback ?: "",
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                }
                            }
                        }
                    } ?: run {
                        // 学习完成
                        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("学习完成！", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("你完成了 $completedCards 张卡片的复习", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { navigator.pop() }) { Text("返回") }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 操作按钮
                    if (currentCard != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 上一张
                            OutlinedButton(
                                onClick = { viewModel.goBack() },
                                enabled = viewModel.canGoBack,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("上一张")
                            }

                            if (!isFlipped) {
                                Button(
                                    onClick = { viewModel.flipCard() },
                                    modifier = Modifier.weight(2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("显示答案")
                                }
                            }
                        }

                        if (isFlipped) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("你记得这个答案吗？", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RatingButton(
                                    modifier = Modifier.weight(1f),
                                    rating = Rating.AGAIN, label = "忘记",
                                    icon = Icons.Default.Close,
                                    color = MaterialTheme.colorScheme.error,
                                    onClick = { viewModel.rateCard(Rating.AGAIN) }
                                )
                                RatingButton(
                                    modifier = Modifier.weight(1f),
                                    rating = Rating.HARD, label = "困难",
                                    icon = null,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    onClick = { viewModel.rateCard(Rating.HARD) }
                                )
                                RatingButton(
                                    modifier = Modifier.weight(1f),
                                    rating = Rating.GOOD, label = "良好",
                                    icon = null,
                                    color = MaterialTheme.colorScheme.primary,
                                    onClick = { viewModel.rateCard(Rating.GOOD) }
                                )
                                RatingButton(
                                    modifier = Modifier.weight(1f),
                                    rating = Rating.EASY, label = "简单",
                                    icon = Icons.Default.Check,
                                    color = MaterialTheme.colorScheme.secondary,
                                    onClick = { viewModel.rateCard(Rating.EASY) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CardContent(card: Card, isFlipped: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (isFlipped) "答案" else "问题",
            style = MaterialTheme.typography.labelLarge,
            color = if (isFlipped) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (card.type) {
            CardType.BASIC, CardType.MARKDOWN, CardType.AI_GENERATED -> {
                MarkdownText(
                    markdown = if (isFlipped) card.back else card.front,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            CardType.REVERSED -> {
                Text(
                    if (isFlipped) card.front else card.back,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
            CardType.CLOZE -> {
                val text = if (isFlipped) card.back else card.front
                val displayText = if (!isFlipped) {
                    text.replace(Regex("\\{\\{c\\d+::([^}]+)}}"), "____")
                        .replace(Regex("\\{\\{c\\d+::([^}]+)::([^}]+)}}"), "____")
                } else {
                    text.replace(Regex("\\{\\{c\\d+::([^}]+)}}"), "$1")
                        .replace(Regex("\\{\\{c\\d+::([^}]+)::([^}]+)}}"), "$1")
                }
                Text(
                    displayText,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                if (!isFlipped) {
                    Spacer(Modifier.height(8.dp))
                    Text("点击显示答案查看填空内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CardType.MULTIPLE_CHOICE -> {
                val lines = (if (isFlipped) card.back else card.front).split("\n")
                val question = lines.firstOrNull() ?: ""
                val options = lines.drop(1)
                Text(question, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                options.forEach { opt ->
                    val isCorrect = if (isFlipped) opt.startsWith("✓") || opt.startsWith("√") else false
                    val displayOpt = opt.removePrefix("✓").removePrefix("√").trim()
                    FilterChip(
                        selected = isCorrect,
                        onClick = {},
                        label = { Text(displayOpt) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
            }
            CardType.IMAGE_OCCLUSION -> {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(if (isFlipped) card.back else "点击显示答案查看图片说明", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            CardType.AUDIO -> {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(if (isFlipped) card.back else card.front, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            CardType.VIDEO -> {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(if (isFlipped) card.back else card.front, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RatingButton(
    modifier: Modifier = Modifier,
    rating: Rating,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label)
    }
}
