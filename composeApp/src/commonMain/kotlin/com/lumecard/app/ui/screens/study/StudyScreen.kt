package com.lumecard.app.ui.screens.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.platform.SoundSettings
import com.lumecard.app.platform.playRatingSound
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.components.LumeCardRatingBar
import com.lumecard.app.ui.components.ProgressRing
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.app.ui.screens.settings.AnswerDisplayMode
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.ui.screens.settings.SettingsStateHolder
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Rating
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.abs

private const val SWIPE_ANIM_DURATION_MS = 200
private const val SWIPE_VELOCITY_THRESHOLD = 800f
private const val SWIPE_THRESHOLD_RATIO = 0.2f

class StudyScreen(
    private val deckIds: List<String>,
    private val deckName: String,
    private val planIds: List<String> = emptyList(),
    private val initialMode: CardsStudyMode = CardsStudyMode.DUE_FIRST,
) : Screen {
    constructor(deckId: String, deckName: String) : this(listOf(deckId), deckName)

    override val key: ScreenKey = "Study_${deckIds.sorted().joinToString("_")}_${planIds.sorted().joinToString("_")}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("OverloadResolutionAmbiguity")
    @Composable
    override fun Content() {
        var crashError by remember { mutableStateOf<String?>(null) }
        val strings = koinInject<I18nManager>().strings

        if (crashError != null) {
            @Suppress("DEPRECATION")
            val clipboardManager = LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { crashError = null },
                title = { Text(strings.crashCompositionError) },
                text = {
                    Column {
                        Text(strings.crashRenderErrorDesc, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = crashError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            crashError?.let { clipboardManager.setText(AnnotatedString(it)) }
                        }, interactionSource = null) { Text(strings.actionCopy) }
                        Button(onClick = { crashError = null }, interactionSource = null) { Text(strings.actionOk) }
                    }
                },
            )
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val viewModel: StudyViewModel = koinInject()
        val settingsState: SettingsStateHolder = koinInject()
        val cards by viewModel.cards.collectAsState()
        val currentIndex by viewModel.currentCardIndex.collectAsState()
        val isFlipped by viewModel.isFlipped.collectAsState()
        val completedCards by viewModel.completedCards.collectAsState()
        val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
        val error by viewModel.error.collectAsState()

        val currentCard = cards.getOrNull(currentIndex)
        val nextCard = cards.getOrNull(currentIndex + 1)
        val scope = rememberCoroutineScope()
        val swipeOffset = remember { Animatable(0f) }
        var isAnimatingOut by remember { mutableStateOf(false) }

        var lastDragTime by remember { mutableLongStateOf(0L) }
        var lastDragX by remember { mutableFloatStateOf(0f) }
        var velocity by remember { mutableFloatStateOf(0f) }

        var showStudyModeDialog by remember { mutableStateOf(false) }
        var soundEnabled by remember { mutableStateOf(SoundSettings.enabled) }
        var hasChosenMode by remember { mutableStateOf(false) }
        val allDone = cards.isNotEmpty() && currentIndex >= cards.size
        LaunchedEffect(allDone, cards.isEmpty()) {
            if (!hasChosenMode && (allDone || (cards.isEmpty() && viewModel.totalCardCount > 0))) {
                showStudyModeDialog = true
            }
        }
        LaunchedEffect(cards) {
            if (cards.isNotEmpty()) hasChosenMode = false
        }

        val dailyLimit = settingsState.newCardsPerDay
        val totalAvail = viewModel.totalCardCount
        val unlearnedAvail = viewModel.unlearnedCardCount

        if (error != null) {
            @Suppress("DEPRECATION")
            val clipboardManager = LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { crashError = null },
                title = { Text(strings.crashCompositionError) },
                text = {
                    Column {
                        Text(strings.crashRenderErrorDesc, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = crashError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { crashError = null }, interactionSource = null) {
                            Text(strings.actionCancel)
                        }
                        Button(onClick = {
                            crashError?.let { clipboardManager.setText(AnnotatedString(it)) }
                        }, interactionSource = null) {
                            Text(strings.actionCopy)
                        }
                        Button(onClick = { viewModel.clearError() }, interactionSource = null) {
                            Text(strings.actionOk)
                        }
                    }
                },
            )
        }

        LaunchedEffect(deckIds) {
            viewModel.loadCards(deckIds, planIds, initialMode)
        }

        LaunchedEffect(currentCard) {
            swipeOffset.snapTo(0f)
            isAnimatingOut = false
        }

        if (showStudyModeDialog) {
            val newCardLabel = if (unlearnedAvail > 0) {
                val actual = unlearnedAvail.coerceAtMost(dailyLimit)
                strings.studyNewCards(actual)
            } else null
            val randomLabel = {
                val actual = totalAvail.coerceAtMost(dailyLimit)
                strings.studyRandom(actual)
            }
            AlertDialog(
                onDismissRequest = { scope.launch { withFrameNanos { navigator.pop() } } },
                title = { Text(strings.studyModeTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.studyModeDesc, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                hasChosenMode = true
                                showStudyModeDialog = false
                                viewModel.reloadWithMode(CardsStudyMode.ALL_CARDS)
                            },
                            interactionSource = null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(strings.studyContinueAll) }
                        Spacer(Modifier.height(4.dp))
                        if (newCardLabel != null) {
                            OutlinedButton(
                                onClick = {
                                    hasChosenMode = true
                                    showStudyModeDialog = false
                                    viewModel.reloadWithMode(CardsStudyMode.NEW_CARDS, dailyLimit)
                                },
                                interactionSource = null,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(newCardLabel) }
                            Spacer(Modifier.height(4.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                hasChosenMode = true
                                showStudyModeDialog = false
                                viewModel.reloadWithMode(CardsStudyMode.RANDOM, dailyLimit)
                            },
                            interactionSource = null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(randomLabel()) }
                    }
                },
                confirmButton = { TextButton(onClick = { scope.launch { withFrameNanos { navigator.pop() } } }, interactionSource = null) { Text(strings.actionDone) } }
            )
        }

        DisposableEffect(Unit) {
            onDispose {
                viewModel.savePlanProgress()
                viewModel.stopTimer()
            }
        }


        BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
            val screenWidth = maxWidth.value
            val threshold = screenWidth * SWIPE_THRESHOLD_RATIO

            Scaffold(
                topBar = {
                    LumeCardTopBar(
                        title = "${strings.actionLearning}: $deckName",
                        onBack = { scope.launch { withFrameNanos { navigator.pop() } } },
                        action = {
                            IconButton(onClick = {
                                soundEnabled = !soundEnabled
                                SoundSettings.enabled = soundEnabled
                            }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = if (soundEnabled) "Mute" else "Unmute",
                                    tint = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                )
                            }
                        }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                strings.studyProgressText(if (currentIndex < cards.size) currentIndex + 1 else cards.size, cards.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatElapsedTime(elapsedSeconds, strings),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
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
                        Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors()) {
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
                                    Button(onClick = { scope.launch { withFrameNanos { navigator.pop() } } }, interactionSource = null) {
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
                                        val nextHCenter = (nextCard.type == CardType.BASIC || nextCard.type == CardType.REVERSED) && nextCard.metadata["hcenter"].toBoolean()
                                        val nextVCenter = (nextCard.type == CardType.BASIC || nextCard.type == CardType.REVERSED) && nextCard.metadata["vcenter"].toBoolean()
                                        val nextFontSize = nextCard.metadata["fontSize"]?.toIntOrNull() ?: 16
                                        val nextFontFamily = FontRegistry.resolveFontFamily(nextCard.metadata["fontFamily"] ?: "")
                                        CardContent(card = nextCard, isFlipped = false, displayMode = settingsState.answerDisplayMode, horizontalCenter = nextHCenter, verticalCenter = nextVCenter, fontSize = nextFontSize, fontFamily = nextFontFamily)
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
                                                lastDragTime = Clock.System.now().toEpochMilliseconds()
                                                lastDragX = 0f
                                                velocity = 0f
                                            },
                                            onDragEnd = {
                                                if (isAnimatingOut) return@detectHorizontalDragGestures
                                                val offset = swipeOffset.value
                                                val velocityTrigger = abs(velocity) > SWIPE_VELOCITY_THRESHOLD
                                                val distanceTrigger = abs(offset) > threshold
                                                if (distanceTrigger || velocityTrigger) {
                                                    isAnimatingOut = true
                                                    val target = if (offset > 0 || velocity > 0) screenWidth * 1.5f else -screenWidth * 1.5f
                                                    scope.launch {
                                                        swipeOffset.animateTo(
                                                            targetValue = target,
                                                            animationSpec = tween(SWIPE_ANIM_DURATION_MS, easing = FastOutLinearInEasing)
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
                                                val now = Clock.System.now().toEpochMilliseconds()
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
                                        isFlipped -> MaterialTheme.colorScheme.surface
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                        val isBasicCard = currentCard.type == CardType.BASIC || currentCard.type == CardType.REVERSED
                                val hCenter = isBasicCard && currentCard.metadata["hcenter"].toBoolean()
                                val vCenter = isBasicCard && currentCard.metadata["vcenter"].toBoolean()
                                val fontSize = currentCard.metadata["fontSize"]?.toIntOrNull() ?: 16
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                        .then(if (vCenter) Modifier else Modifier.verticalScroll(rememberScrollState())),
                                    contentAlignment = if (vCenter) Alignment.Center else Alignment.TopStart
                                ) {
                                    val onConfirmChoice: (() -> Unit)? = remember(currentCard) {
                                        if (currentCard.type == CardType.MULTIPLE_CHOICE) {
                                            { viewModel.flipCard() }
                                        } else null
                                    }
                                    CardContent(
                                        card = currentCard,
                                        isFlipped = isFlipped,
                                        displayMode = settingsState.answerDisplayMode,
                                        horizontalCenter = hCenter,
                                        verticalCenter = vCenter,
                                        fontSize = fontSize,
                                        fontFamily = FontRegistry.resolveFontFamily(currentCard.metadata["fontFamily"] ?: ""),
                                        onConfirmChoice = onConfirmChoice,
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
                        Card(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shape = LumeCardTheme.radius.card,
                            colors = CardDefaults.cardColors(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))

                                ProgressRing(
                                    progress = 1f,
                                    size = 88.dp,
                                    strokeWidth = 7.dp,
                                    trackColor = LumeCardTheme.semanticColors.progressTrack,
                                    progressColor = LumeCardTheme.semanticColors.progressFill,
                                    showPercentage = false,
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Surface(
                                    shape = LumeCardTheme.radius.pill,
                                    color = LumeCardTheme.semanticColors.progressFill.copy(alpha = 0.15f),
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = LumeCardTheme.semanticColors.progressFill,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    strings.studyComplete,
                                    style = LumeCardTheme.typography.heading2,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    strings.studyCompleteMsg(completedCards),
                                    style = LumeCardTheme.typography.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    CompletionStat(
                                        value = "$completedCards",
                                        label = strings.studyCompleteReviewed,
                                    )
                                    CompletionStat(
                                        value = formatElapsedTime(elapsedSeconds, strings),
                                        label = strings.studyCompleteTimeSpent,
                                    )
                                    CompletionStat(
                                        value = "${(completedCards * 75 + 100).coerceAtMost(999)}",
                                        label = strings.studyCompleteXpEarned,
                                    )
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { scope.launch { withFrameNanos { navigator.pop() } } },
                                        interactionSource = null,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(strings.actionDone)
                                    }
                                    Button(
                                        onClick = { scope.launch { withFrameNanos { navigator.pop() } } },
                                        interactionSource = null,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(strings.dashStartLearning)
                                    }
                                }
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
                                interactionSource = null,
                                enabled = viewModel.canGoBack,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.studyPreviousCard)
                            }

                            if (!isFlipped && currentCard.type != CardType.MULTIPLE_CHOICE) {
                                Button(
                                    onClick = { viewModel.flipCard() },
                                    interactionSource = null,
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
                                onAgain = { if (soundEnabled) playRatingSound(Rating.AGAIN); viewModel.rateCard(Rating.AGAIN) },
                                onHard = { if (soundEnabled) playRatingSound(Rating.HARD); viewModel.rateCard(Rating.HARD) },
                                onGood = { if (soundEnabled) playRatingSound(Rating.GOOD); viewModel.rateCard(Rating.GOOD) },
                                onEasy = { if (soundEnabled) playRatingSound(Rating.EASY); viewModel.rateCard(Rating.EASY) },
                                strings = strings,
                            )
                        }
                    }
                }
            }
        }
    }
}
