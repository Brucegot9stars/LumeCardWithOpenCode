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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.components.MarkdownText
import com.lumecard.app.ui.screens.settings.AnswerDisplayMode
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.screens.settings.SettingsStateHolder
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Rating
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

class StudyScreen(
    private val deckIds: List<String>,
    private val deckName: String
) : Screen {
    constructor(deckId: String, deckName: String) : this(listOf(deckId), deckName)

    override val key: ScreenKey = "Study_${deckIds.sorted().joinToString("_")}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: StudyViewModel = koinInject()
        val settingsState: SettingsStateHolder = koinInject()
        val strings = koinInject<I18nManager>().strings
        val cards by viewModel.cards.collectAsState()
        val currentIndex by viewModel.currentCardIndex.collectAsState()
        val isFlipped by viewModel.isFlipped.collectAsState()
        val completedCards by viewModel.completedCards.collectAsState()

        val currentCard = cards.getOrNull(currentIndex)
        val nextCard = cards.getOrNull(currentIndex + 1)
        val scope = rememberCoroutineScope()
        val swipeOffset = remember { Animatable(0f) }
        var isAnimatingOut by remember { mutableStateOf(false) }

        var lastDragTime by remember { mutableLongStateOf(0L) }
        var lastDragX by remember { mutableFloatStateOf(0f) }
        var velocity by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(deckIds) {
            viewModel.loadCards(deckIds)
        }

        LaunchedEffect(currentCard) {
            swipeOffset.snapTo(0f)
            isAnimatingOut = false
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = maxWidth.value
            val threshold = screenWidth * 0.2f

            topBar = {
                LumeCardTopBar(
                    title = "{strings.actionLearning}: deckName",
                    onBack = { navigator.pop() }
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
                            strings.studyProgressText(if (currentIndex < cards.size) currentIndex + 1 else cards.size, cards.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentCard != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = cardTypeName(currentCard.type),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (cards.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        strings.studyNoCards,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        strings.studyGoCreateCards,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Button(onClick = { navigator.pop() }) {
                                        Text(strings.actionBack)
                                    }
                                }
                            }
                        }
                    } else if (currentCard != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (nextCard != null && !isAnimatingOut) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, start = 4.dp, end = 4.dp)
                                        .graphicsLayer {
                                            scaleX = 0.95f
                                            scaleY = 0.95f
                                        }
                                        .zIndex(0f),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CardContent(card = nextCard, isFlipped = false, displayMode = settingsState.answerDisplayMode)
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = swipeOffset.value
                                        rotationZ = swipeOffset.value / 25f
                                        val scaleReduction = (abs(swipeOffset.value) / (screenWidth * 8f)).coerceIn(0f, 0.1f)
                                        scaleX = 1f - scaleReduction
                                        scaleY = 1f - scaleReduction
                                    }
                                    .zIndex(1f)
                                    .pointerInput(currentCard.id) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                if (isAnimatingOut) return@detectHorizontalDragGestures
                                                lastDragTime = System.currentTimeMillis()
                                                lastDragX = 0f
                                                velocity = 0f
                                            },
                                            onDragEnd = {
                                                if (isAnimatingOut) return@detectHorizontalDragGestures
                                                val offset = swipeOffset.value
                                                val velocityTrigger = abs(velocity) > 800f
                                                val distanceTrigger = abs(offset) > threshold
                                                if (distanceTrigger || velocityTrigger) {
                                                    isAnimatingOut = true
                                                    val target = if (offset > 0 || velocity > 0) screenWidth * 1.5f else -screenWidth * 1.5f
                                                    scope.launch {
                                                        swipeOffset.animateTo(
                                                            targetValue = target,
                                                            animationSpec = tween(200, easing = FastOutLinearInEasing)
                                                        )
                                                        swipeOffset.snapTo(0f)
                                                        isAnimatingOut = false
                                                        if (offset > 0 || velocity > 0) {
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
                                                val now = System.currentTimeMillis()
                                                val dt = (now - lastDragTime).coerceAtLeast(1)
                                                velocity = dragAmount / dt * 1000f
                                                lastDragTime = now
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
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    CardContent(
                                        card = currentCard,
                                        isFlipped = isFlipped,
                                        displayMode = settingsState.answerDisplayMode
                                    )
                                }
                            }

                            val leftAlpha = (-swipeOffset.value / threshold).coerceIn(0f, 1f)
                            val rightAlpha = (swipeOffset.value / threshold).coerceIn(0f, 1f)

                            if (leftAlpha > 0.01f && !isAnimatingOut) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(16.dp)
                                        .zIndex(2f)
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
                                            Text(strings.studySwipeEasy, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }

                            if (rightAlpha > 0.01f && !isAnimatingOut) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(16.dp)
                                        .zIndex(2f)
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
                                            Text(strings.studySwipeBack, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(strings.studyComplete, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(strings.studyCompleteMsg(completedCards), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { navigator.pop() }) { Text(strings.actionBack) }
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
                                Text(strings.studyPreviousCard)
                            }

                            if (!isFlipped) {
                                Button(
                                    onClick = { viewModel.flipCard() },
                                    modifier = Modifier.weight(2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(strings.studyShowAnswer)
                                }
                            }
                        }

                        if (isFlipped) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                strings.studyRatingPrompt,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LumeCardRatingBar(
                                onAgain = { viewModel.rateCard(Rating.AGAIN) },
                                onHard = { viewModel.rateCard(Rating.HARD) },
                                onGood = { viewModel.rateCard(Rating.GOOD) },
                                onEasy = { viewModel.rateCard(Rating.EASY) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardContent(
    card: Card,
    isFlipped: Boolean,
    displayMode: AnswerDisplayMode
) {
    val strings = koinInject<I18nManager>().strings
    Column(modifier = Modifier.fillMaxWidth()) {
        when (displayMode) {
            AnswerDisplayMode.FLIP -> {
                FlipCard(
                    isFlipped = isFlipped,
                    front = { CardFace(card, showBack = false) },
                    back = { CardFace(card, showBack = true) }
                )
            }
            AnswerDisplayMode.SPLIT -> {
                if (isFlipped) {
                    // Question section
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    strings.studyQuestion,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            Spacer(Modifier.weight(1f))
                            Text(
                                cardTypeName(card.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ) {
                        CardFace(card, showBack = false).let { it }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Answer section
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    strings.studyAnswer,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            Spacer(Modifier.weight(1f))
                            Text(
                                    strings.studyRevealed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    ) {
                        CardFace(card, showBack = true).let { it }
                    }
                } else {
                    CardFace(card, showBack = false)
                }
            }
        }
    }
}

@Composable
private fun FlipCard(
    isFlipped: Boolean,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation = remember { Animatable(if (isFlipped) 180f else 0f) }
    var cardWidth by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(isFlipped) {
        rotation.animateTo(
            targetValue = if (isFlipped) 180f else 0f,
            animationSpec = tween(500)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { cardWidth = it.width.coerceAtLeast(1).toFloat() }
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = cardWidth * 8f
            }
    ) {
        if (rotation.value < 90f) {
            front()
        } else {
            Box(modifier = Modifier.graphicsLayer { scaleX = -1f }) {
                back()
            }
        }
    }
}

@Composable
private fun CardFace(card: Card, showBack: Boolean) {
    val strings = koinInject<I18nManager>().strings
    Column(modifier = Modifier.fillMaxWidth()) {
        when (card.type) {
            CardType.BASIC, CardType.REVERSED, CardType.MARKDOWN, CardType.AI_GENERATED -> {
                MarkdownText(
                    markdown = if (showBack) card.back else card.front,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CardType.CLOZE -> {
                val text = if (showBack) card.back else card.front
                val displayText = if (!showBack) {
                    text.replace(Regex("\\{\\{c\\d+::([^}]+)}}"), "____")
                        .replace(Regex("\\{\\{c\\d+::([^}]+)::([^}]+)}}"), "____")
                } else {
                    text.replace(Regex("\\{\\{c\\d+::([^}]+)}}"), "$1")
                        .replace(Regex("\\{\\{c\\d+::([^}]+)::([^}]+)}}"), "$1")
                }
                Text(
                    displayText,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (!showBack) {
                    Spacer(Modifier.height(8.dp))
                    Text(strings.studyClozeHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CardType.MULTIPLE_CHOICE -> {
                val lines = (if (showBack) card.back else card.front).split("\n")
                val question = lines.firstOrNull() ?: ""
                val options = lines.drop(1)
                Text(question, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                options.forEach { opt ->
                    val isCorrect = if (showBack) opt.startsWith("✓") || opt.startsWith("√") else false
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
                Text(if (showBack) card.back else strings.studyImageHint, style = MaterialTheme.typography.bodyMedium)
            }
            CardType.AUDIO, CardType.VIDEO -> {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                MarkdownText(
                    markdown = if (showBack) card.back else card.front,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun cardTypeName(type: CardType): String {
    val strings = koinInject<I18nManager>().strings
    return when (type) {
        CardType.BASIC -> strings.studyCardTypeBasic
        CardType.REVERSED -> strings.studyCardTypeReversed
        CardType.CLOZE -> strings.studyCardTypeCloze
        CardType.MULTIPLE_CHOICE -> strings.studyCardTypeChoice
        CardType.IMAGE_OCCLUSION -> strings.studyCardTypeOcclusion
        CardType.AUDIO -> strings.studyCardTypeAudio
        CardType.VIDEO -> strings.studyCardTypeVideo
        CardType.MARKDOWN -> strings.studyCardTypeMarkdown
        CardType.AI_GENERATED -> strings.studyCardTypeAi
    }
}



