package com.lumecard.app.ui.screens.study

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.navigation.StudyScreen
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Rating

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreenContent(
    deckId: String,
    deckName: String
) {
    val navigator = LocalNavigator.currentOrThrow
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var completedCards by remember { mutableIntStateOf(0) }
    var startTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val currentCard = cards.getOrNull(currentIndex)

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
                    progress = { (currentIndex.toFloat() / cards.size) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "进度: ${currentIndex + 1} / ${cards.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 卡片展示
            currentCard?.let { card ->
                AnimatedContent(
                    targetState = isFlipped,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
                    },
                    label = "card_flip"
                ) { flipped ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (flipped) 4.dp else 8.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (flipped) 
                                MaterialTheme.colorScheme.secondaryContainer
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (flipped) {
                                    Text(
                                        "答案",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        card.back,
                                        style = MaterialTheme.typography.headlineSmall,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        "问题",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        card.front,
                                        style = MaterialTheme.typography.headlineSmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 操作按钮
                if (isFlipped) {
                    // 评分按钮
                    Text(
                        "你记得这个答案吗？",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RatingButton(
                            modifier = Modifier.weight(1f),
                            rating = Rating.AGAIN,
                            label = "忘记",
                            icon = Icons.Default.Close,
                            color = MaterialTheme.colorScheme.error,
                            onClick = {
                                // 评分: 忘记
                                moveToNext()
                            }
                        )
                        RatingButton(
                            modifier = Modifier.weight(1f),
                            rating = Rating.HARD,
                            label = "困难",
                            icon = null,
                            color = MaterialTheme.colorScheme.tertiary,
                            onClick = {
                                // 评分: 困难
                                moveToNext()
                            }
                        )
                        RatingButton(
                            modifier = Modifier.weight(1f),
                            rating = Rating.GOOD,
                            label = "良好",
                            icon = null,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                // 评分: 良好
                                moveToNext()
                            }
                        )
                        RatingButton(
                            modifier = Modifier.weight(1f),
                            rating = Rating.EASY,
                            label = "简单",
                            icon = Icons.Default.Check,
                            color = MaterialTheme.colorScheme.secondary,
                            onClick = {
                                // 评分: 简单
                                moveToNext()
                            }
                        )
                    }
                } else {
                    // 翻转按钮
                    Button(
                        onClick = { isFlipped = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("显示答案")
                    }
                }
            } ?: run {
                // 无卡片或学习完成
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "学习完成！",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "你完成了 $completedCards 张卡片的复习",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { navigator.pop() }
                        ) {
                            Text("返回")
                        }
                    }
                }
            }
        }
    }

    fun moveToNext() {
        isFlipped = false
        completedCards++
        if (currentIndex < cards.size - 1) {
            currentIndex++
        } else {
            // 学习完成
            currentIndex = cards.size
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
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label)
    }
}
