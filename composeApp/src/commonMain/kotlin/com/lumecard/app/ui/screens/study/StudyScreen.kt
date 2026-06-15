package com.lumecard.app.ui.screens.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

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

        val currentCard = cards.getOrNull(currentIndex)
        val scope = rememberCoroutineScope()
        val swipeOffset = remember { Animatable(0f) }
        var isAnimatingOut by remember { mutableStateOf(false) }

        LaunchedEffect(deckId) {
            viewModel.loadCards(deckId)
        }

        LaunchedEffect(currentCard) {
            swipeOffset.snapTo(0f)
            isAnimatingOut = false
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = maxWidth.value
            val threshold = screenWidth * 0.3f
            val screenHeight = maxHeight.value

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("学习: $deckName") },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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

                    currentCard?.let { card ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = swipeOffset.value
                                        rotationZ = swipeOffset.value / 20f
                                        scaleX = 1f - (abs(swipeOffset.value) / (screenWidth * 5f)).coerceIn(0f, 0.15f)
                                        scaleY = 1f - (abs(swipeOffset.value) / (screenWidth * 5f)).coerceIn(0f, 0.15f)
                                    }
                                    .pointerInput(card.id) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                if (isAnimatingOut) return@detectHorizontalDragGestures
                                            },
                                            onDragEnd = {
                                                if (isAnimatingOut) return@detectHorizontalDragGestures
                                                val offset = swipeOffset.value
                                                if (abs(offset) > threshold) {
                                                    isAnimatingOut = true
                                                    val target = if (offset > 0) screenWidth * 1.5f else -screenWidth * 1.5f
                                                    scope.launch {
                                                        swipeOffset.animateTo(
                                                            targetValue = target,
                                                            animationSpec = tween(250, easing = FastOutLinearInEasing)
                                                        )
                                                        swipeOffset.snapTo(0f)
                                                        isAnimatingOut = false
                                                        if (offset > 0) {
                                                            if (viewModel.canGoBack) viewModel.goBack()
                                                        } else {
                                                            viewModel.rateCard(Rating.EASY)
                                                        }
                                                    }
                                                } else {
                                                    scope.launch {
                                                        swipeOffset.animateTo(0f, spring())
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                scope.launch {
                                                    swipeOffset.animateTo(0f, spring())
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                if (isAnimatingOut) return@detectHorizontalDragGestures
                                                change.consume()
                                                val newOffset = (swipeOffset.value + dragAmount)
                                                    .coerceIn(-screenWidth * 2f, screenWidth * 2f)
                                                scope.launch {
                                                    swipeOffset.snapTo(newOffset)
                                                }
                                            }
                                        )
                                    },
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        swipeOffset.value > 10f -> MaterialTheme.colorScheme.primaryContainer
                                        swipeOffset.value < -10f -> MaterialTheme.colorScheme.secondaryContainer
                                        isFlipped -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp)
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CardContent(card = card, isFlipped = isFlipped)
                                }
                            }

                            val leftAlpha = (-swipeOffset.value / threshold).coerceIn(0f, 1f)
                            val rightAlpha = (swipeOffset.value / threshold).coerceIn(0f, 1f)

                            if (leftAlpha > 0.01f && !isAnimatingOut) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(16.dp)
                                        .graphicsLayer {
                                            alpha = leftAlpha
                                            scaleX = 1f + leftAlpha * 0.5f
                                            scaleY = 1f + leftAlpha * 0.5f
                                        }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("简单", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }

                            if (rightAlpha > 0.01f && !isAnimatingOut) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(16.dp)
                                        .graphicsLayer {
                                            alpha = rightAlpha
                                            scaleX = 1f + rightAlpha * 0.5f
                                            scaleY = 1f + rightAlpha * 0.5f
                                        }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("返回", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    } ?: run {
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

                    if (currentCard != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.goBack() },
                                enabled = viewModel.canGoBack,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                RatingButton(Modifier.weight(1f), Rating.AGAIN, "忘记", Icons.Default.Close, MaterialTheme.colorScheme.error) {
                                    viewModel.rateCard(Rating.AGAIN)
                                }
                                RatingButton(Modifier.weight(1f), Rating.HARD, "困难", null, MaterialTheme.colorScheme.tertiary) {
                                    viewModel.rateCard(Rating.HARD)
                                }
                                RatingButton(Modifier.weight(1f), Rating.GOOD, "良好", null, MaterialTheme.colorScheme.primary) {
                                    viewModel.rateCard(Rating.GOOD)
                                }
                                RatingButton(Modifier.weight(1f), Rating.EASY, "简单", Icons.Default.Check, MaterialTheme.colorScheme.secondary) {
                                    viewModel.rateCard(Rating.EASY)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardContent(card: Card, isFlipped: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopStart),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Text(
                text = cardTypeName(card.type),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                CardType.AUDIO, CardType.VIDEO -> {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    MarkdownText(
                        markdown = if (isFlipped) card.back else card.front,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun cardTypeName(type: CardType): String = when (type) {
    CardType.BASIC -> "基础"
    CardType.REVERSED -> "反转"
    CardType.CLOZE -> "填空"
    CardType.MULTIPLE_CHOICE -> "选择"
    CardType.IMAGE_OCCLUSION -> "遮挡"
    CardType.AUDIO -> "音频"
    CardType.VIDEO -> "视频"
    CardType.MARKDOWN -> "Markdown"
    CardType.AI_GENERATED -> "AI"
}

@Composable
private fun RatingButton(
    modifier: Modifier = Modifier,
    rating: Rating,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    color: Color,
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
