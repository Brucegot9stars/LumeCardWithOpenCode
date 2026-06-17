package com.lumecard.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.components.CompactProgressBar
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.components.ProgressRing
import com.lumecard.app.ui.screens.deck.CardListScreen
import com.lumecard.app.ui.screens.deck.DeckListScreen
import com.lumecard.app.ui.screens.knowledgebase.KnowledgeBaseScreen
import com.lumecard.app.ui.screens.learningplan.LearningPlanSelectionScreen
import com.lumecard.app.ui.screens.study.StudyModeScreen
import com.lumecard.app.ui.screens.study.StudyScreen
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.model.Deck
import org.koin.compose.koinInject

class DashboardScreen : Screen {
    override val key: ScreenKey = "Dashboard"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: DashboardViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val semanticColors = LumeCardTheme.semanticColors

        val decks by viewModel.decks.collectAsState()
        val decksWithCount by viewModel.decksWithCount.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val todayReviews by viewModel.todayReviews.collectAsState()
        val dailyGoal by viewModel.dailyGoal.collectAsState()
        val totalDueCards by viewModel.totalDueCards.collectAsState()
        val kbCount by viewModel.kbCount.collectAsState()
        val activePlanCount by viewModel.activePlanCount.collectAsState()

        val firstStudyableDeck = decksWithCount.firstOrNull { it.cardCount > 0 }?.deck
        val studyableCount = decksWithCount.count { it.cardCount > 0 }
        val totalCards = decksWithCount.sumOf { it.cardCount }
        val goalProgress = if (dailyGoal > 0) (todayReviews.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f

        Scaffold(
            topBar = {
                LumeCardTopBar(title = strings.appName)
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(KnowledgeBaseScreen()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.dashManageDecks)
                }
            }
        ) { padding ->
            if (isLoading && decks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.section),
                ) {
                    // ── Today Study Card ──────────────────────────────────
                    item(key = "overview") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = radius.card,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Progress ring
                                ProgressRing(
                                    progress = goalProgress,
                                    size = 80.dp,
                                    strokeWidth = 7.dp,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                                    progressColor = MaterialTheme.colorScheme.primary,
                                )

                                Spacer(modifier = Modifier.width(spacing.md))

                                // Stats column
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        " /  today",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(spacing.sm))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        StatItem(strings.dashDecksLabel, "${decks.size}")
                                        StatItem(strings.dashTotalCards, "$totalCards")
                                        StatItem(strings.dashDecksWithCards, "$studyableCount")
                                    }
                                }
                            }
                        }
                    }

                    // ── Quick Actions ─────────────────────────────────────
                    item(key = "quickActions") {
                        Column {
                            Text(
                                strings.dashQuickActions,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                QuickActionCard(
                                    modifier = Modifier.weight(1f),
                                    title = strings.dashStartLearning,
                                    subtitle = strings.dashActivePlans(activePlanCount),
                                    enabled = true,
                                    icon = Icons.Default.PlayArrow,
                                    isPrimary = true,
                                    onClick = { navigator.push(LearningPlanSelectionScreen()) },
                                )
                                QuickActionCard(
                                    modifier = Modifier.weight(1f),
                                    title = strings.dashManageDecks,
                                    subtitle = strings.dashKBCount(kbCount),
                                    icon = Icons.AutoMirrored.Filled.List,
                                    isPrimary = false,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    iconBackgroundColor = MaterialTheme.colorScheme.tertiary,
                                    iconTint = MaterialTheme.colorScheme.onTertiary,
                                    onClick = { navigator.push(KnowledgeBaseScreen()) },
                                )
                            }
                        }
                    }

                    // ── My Decks Header ───────────────────────────────────
                    item(key = "decksHeader") {
                        Text(
                            "${strings.dashMyDecks} (${decks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                        )
                    }

                    // ── Decks List ────────────────────────────────────────
                    if (decks.isEmpty()) {
                        item(key = "empty") {
                            EmptyDecksCard(strings.dashBeginJourney, strings.dashJourneyDesc, strings.dashCreateFirstDeck) {
                                navigator.push(DeckListScreen())
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = decksWithCount,
                            key = { _, item -> item.deck.id },
                        ) { index, item ->
                            AnimatedItem(delayMs = index * 60) {
                                DeckItem(
                                    deck = item.deck,
                                    cardCount = item.cardCount,
                                    progress = if (item.cardCount > 0) 0.6f else 0f,
                                    onClick = { navigator.push(CardListScreen(item.deck.id, item.deck.name)) },
                                    onStudy = {
                                        if (item.cardCount > 0) {
                                            navigator.push(StudyScreen(item.deck.id, item.deck.name))
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // Bottom spacing
                    item(key = "bottomSpacer") {
                        Spacer(modifier = Modifier.height(spacing.xxl))
                    }
                }
            }
        }
    }

    @Composable
    private fun StatItem(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun QuickActionCard(
        modifier: Modifier = Modifier,
        title: String,
        subtitle: String? = null,
        enabled: Boolean = true,
        icon: ImageVector,
        isPrimary: Boolean = false,
        containerColor: Color = MaterialTheme.colorScheme.surface,
        iconBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
        iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick: () -> Unit,
    ) {
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius

        val bgColor = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isPrimary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else -> containerColor
        }
        val iconBg = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isPrimary -> MaterialTheme.colorScheme.primary
            else -> iconBackgroundColor
        }
        val iconT = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            isPrimary -> MaterialTheme.colorScheme.onPrimary
            else -> iconTint
        }

        Card(
            modifier = modifier.fillMaxHeight(),
            shape = radius.card,
            onClick = onClick,
            enabled = enabled,
            colors = CardDefaults.cardColors(containerColor = bgColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = radius.pill,
                    color = iconBg,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = iconT,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }

    @Composable
    private fun DeckItem(
        deck: Deck,
        cardCount: Int,
        progress: Float,
        onClick: () -> Unit,
        onStudy: () -> Unit,
    ) {
        val strings = koinInject<I18nManager>().strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = radius.card,
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Deck emoji with container
                    Surface(
                        shape = radius.button,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                deck.icon,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(spacing.md))

                    // Deck info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            deck.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            strings.dashCardsCount(cardCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (cardCount > 0) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            CompactProgressBar(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                progressColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(spacing.sm))

                    // Study button
                    FilledTonalIconButton(
                        onClick = onStudy,
                        enabled = cardCount > 0,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = strings.actionLearning,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyDecksCard(
        title: String,
        description: String,
        ctaLabel: String,
        onCta: () -> Unit,
    ) {
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val semanticColors = LumeCardTheme.semanticColors

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = radius.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Empty state icon
                Surface(
                    shape = radius.pill,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.md))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.lg))
                FilledTonalButton(onClick = onCta) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Text(ctaLabel)
                }
            }
        }
    }
}

/**
 * Wraps content with a fade-in + slide-up animation, with configurable delay.
 */
@Composable
private fun AnimatedItem(
    delayMs: Int = 0,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300, delayMs)) +
            slideInVertically(
                animationSpec = tween(300, delayMs),
                initialOffsetY = { it / 4 },
            ),
    ) {
        content()
    }
}



