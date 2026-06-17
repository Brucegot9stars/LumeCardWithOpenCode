package com.lumecard.app.ui.screens.learningplan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.screens.study.StudyModeScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.model.PlanStatus
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class LearningPlanSelectionScreen : Screen {
    override val key: ScreenKey = "PlanSelection"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: LearningPlanViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val plans by viewModel.plans.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            viewModel.loadPlans()
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.planTitle,
                    onBack = { navigator.pop() },
                    action = {
                        IconButton(onClick = { navigator.push(LearningPlanScreen()) }) {
                            Icon(Icons.Default.Add, contentDescription = strings.planCreate)
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    // Random Plan
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = radius.card,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            ),
                            onClick = {
                                navigator.push(StudyModeScreen())
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = radius.pill,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onTertiary)
                                    }
                                }
                                Spacer(Modifier.width(spacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(strings.planRandom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(strings.planRandomDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Plan list
                    if (plans.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(spacing.sm))
                            Text(strings.planSelectToStart, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    items(plans, key = { it.id }) { plan ->
                        val progress = if (plan.totalCards > 0) plan.completedCards.toFloat() / plan.totalCards else 0f
                        val statusColor = when (plan.status) {
                            PlanStatus.NOT_STARTED -> MaterialTheme.colorScheme.outline
                            PlanStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                            PlanStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                        }
                        val statusText = when (plan.status) {
                            PlanStatus.NOT_STARTED -> strings.planStatusNotStarted
                            PlanStatus.IN_PROGRESS -> strings.planStatusInProgress
                            PlanStatus.COMPLETED -> strings.planStatusCompleted
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = radius.card,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                            onClick = {
                                scope.launch {
                                    viewModel.startPlan(plan.id)
                                }
                                navigator.push(StudyModeScreen(planIds = listOf(plan.id)))
                            }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(spacing.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(plan.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Surface(shape = radius.pill, color = statusColor.copy(alpha = 0.15f)) {
                                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                    if (plan.isDefault) {
                                        Spacer(Modifier.width(spacing.sm))
                                        Surface(shape = radius.pill, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                            Text(strings.planDefault, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                val desc = plan.description
                                if (desc != null) {
                                    Spacer(Modifier.height(spacing.xs))
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(spacing.sm))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${plan.totalCards} cards", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(spacing.sm))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.weight(1f).height(4.dp),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Spacer(Modifier.width(spacing.sm))
                                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (plans.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(Modifier.height(spacing.sm))
                                    Text(strings.planEmpty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(spacing.xxl)) }
                }
            }
        }
    }
}
