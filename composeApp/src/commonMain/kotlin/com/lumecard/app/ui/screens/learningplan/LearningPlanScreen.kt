package com.lumecard.app.ui.screens.learningplan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTextField
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class LearningPlanScreen(
    private val editPlanId: String? = null
) : Screen {
    override val key: ScreenKey = "LearningPlan_${editPlanId ?: "new"}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: LearningPlanViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val spacing = LumeCardTheme.spacing
        val scope = rememberCoroutineScope()

        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var isDefault by remember { mutableStateOf(false) }
        var showSavedDialog by remember { mutableStateOf(false) }

        LaunchedEffect(editPlanId) {
            if (editPlanId != null) {
                val plan = viewModel.plans.value.find { it.id == editPlanId }
                    ?: viewModel.getPlanById(editPlanId)
                if (plan != null) {
                    name = plan.name
                    description = plan.description ?: ""
                    isDefault = plan.isDefault
                }
            }
        }

        if (showSavedDialog) {
            AlertDialog(
                onDismissRequest = { navigator.pop() },
                title = { Text(if (editPlanId != null) strings.planUpdated else strings.planCreated) },
                text = { Text(if (editPlanId != null) strings.planSavedDescUpdate else strings.planSavedDescCreate) },
                confirmButton = {
                    Button(onClick = { navigator.pop() }) {
                        Text(strings.actionOk)
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = if (editPlanId != null) strings.planEdit else strings.planCreate,
                    onBack = { navigator.pop() }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                LumeCardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = strings.fieldName,
                )
                LumeCardTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = strings.fieldDescription,
                    singleLine = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.planDefault, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
                Spacer(Modifier.height(spacing.md))
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                if (editPlanId != null) {
                                    val existing = viewModel.plans.value.find { it.id == editPlanId }
                                    if (existing != null) {
                                        viewModel.updatePlan(
                                            id = editPlanId,
                                            name = name,
                                            description = description.ifBlank { null },
                                            knowledgeBaseIds = existing.knowledgeBaseIds,
                                            deckIds = existing.deckIds,
                                            cardIds = existing.cardIds,
                                            isDefault = isDefault
                                        )
                                    }
                                } else {
                                    viewModel.createPlan(
                                        name = name,
                                        description = description.ifBlank { null },
                                        knowledgeBaseIds = emptyList(),
                                        deckIds = emptyList(),
                                        cardIds = emptyList(),
                                        isDefault = isDefault
                                    )
                                }
                                showSavedDialog = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank()
                ) {
                    Text(strings.actionSave)
                }
            }
        }
    }
}
