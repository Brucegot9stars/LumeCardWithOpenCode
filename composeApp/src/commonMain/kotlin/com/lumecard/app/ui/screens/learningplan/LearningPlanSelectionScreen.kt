package com.lumecard.app.ui.screens.learningplan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardDialog
import com.lumecard.app.ui.components.LumeCardTextField
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

        val snackbarHostState = remember { SnackbarHostState() }
        var showCreateDialog by remember { mutableStateOf(false) }
        var editPlanId by remember { mutableStateOf<String?>(null) }
        var dialogName by remember { mutableStateOf("") }
        var dialogDesc by remember { mutableStateOf("") }
        var deletePlanId by remember { mutableStateOf<String?>(null) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            viewModel.loadPlans()
        }

        if (errorMsg != null) {
            @Suppress("DEPRECATION")
            val clipboardManager = LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { errorMsg = null },
                title = { Text(strings.errorTitle) },
                text = {
                    Column {
                        Text(strings.errorDesc, style = MaterialTheme.typography.bodyMedium)
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
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            errorMsg?.let { clipboardManager.setText(AnnotatedString(it)) }
                        }) {
                            Text(strings.actionCopy)
                        }
                        Button(onClick = { errorMsg = null }) {
                            Text(strings.actionOk)
                        }
                    }
                },
            )
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.planTitle,
                    onBack = { navigator.pop() },
                    action = {
                        IconButton(onClick = {
                            dialogName = ""
                            dialogDesc = ""
                            editPlanId = null
                            showCreateDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = strings.planCreate)
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
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
                                try {
                                    navigator.push(StudyModeScreen())
                                } catch (e: Exception) {
                                    println("[LumeCard ERROR] PlanSelection navigate random: ${e.message}")
                                    e.printStackTrace()
                                    errorMsg = "navigate: ${e.message}\n${e.stackTraceToString()}"
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(shape = radius.pill, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(44.dp)) {
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
                                    try {
                                        viewModel.startPlan(plan.id)
                                    } catch (e: Exception) {
                                        println("[LumeCard ERROR] PlanSelection startPlan: ${e.message}")
                                        e.printStackTrace()
                                        errorMsg = "startPlan: ${e.message}\n${e.stackTraceToString()}"
                                    }
                                }
                                try {
                                    navigator.push(StudyModeScreen(planIds = listOf(plan.id), preSelectedDeckIds = plan.deckIds))
                                } catch (e: Exception) {
                                    println("[LumeCard ERROR] PlanSelection navigate plan: ${e.message}")
                                    e.printStackTrace()
                                    errorMsg = "navigate: ${e.message}\n${e.stackTraceToString()}"
                                }
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
                                    Spacer(Modifier.width(spacing.xs))
                                    IconButton(onClick = { deletePlanId = plan.id }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.actionDelete, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                                val desc = plan.description
                                if (desc != null) {
                                    Spacer(Modifier.height(spacing.xs))
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(spacing.sm))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(strings.planCardsCount(plan.totalCards), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(spacing.sm))
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(4.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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

        // Create / Edit dialog
        if (showCreateDialog) {
            LumeCardDialog(
                title = if (editPlanId != null) strings.planEdit else strings.planCreate,
                onDismiss = { showCreateDialog = false },
                onConfirm = {
                    scope.launch {
                        val saved = try {
                            if (editPlanId != null) {
                                val existing = viewModel.plans.value.find { it.id == editPlanId }
                                if (existing != null) {
                                    viewModel.updatePlan(editPlanId!!, dialogName, dialogDesc.ifBlank { null }, existing.knowledgeBaseIds, existing.deckIds, existing.cardIds, existing.isDefault)
                                }
                            } else {
                                viewModel.createPlan(dialogName, dialogDesc.ifBlank { null }, emptyList(), emptyList(), emptyList())
                            }
                            true
                        } catch (_: Exception) { false }
                        showCreateDialog = false
                        snackbarHostState.showSnackbar(
                            message = if (saved) (if (editPlanId != null) strings.planUpdated else strings.planCreated) else strings.errorDesc,
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                confirmText = strings.actionSave,
                confirmEnabled = dialogName.isNotBlank(),
            ) {
                LumeCardTextField(value = dialogName, onValueChange = { dialogName = it }, label = strings.fieldName)
                LumeCardTextField(value = dialogDesc, onValueChange = { dialogDesc = it }, label = strings.fieldDescription, singleLine = false)
            }
        }

        // Delete confirmation dialog
        if (deletePlanId != null) {
            AlertDialog(
                onDismissRequest = { deletePlanId = null },
                title = { Text(strings.planDeleteConfirm) },
                text = { Text(strings.planDeleteConfirmDesc) },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                deletePlanId?.let { viewModel.deletePlan(it) }
                                deletePlanId = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.actionDelete)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { deletePlanId = null }) {
                        Text(strings.actionCancel)
                    }
                }
            )
        }
    }
}
