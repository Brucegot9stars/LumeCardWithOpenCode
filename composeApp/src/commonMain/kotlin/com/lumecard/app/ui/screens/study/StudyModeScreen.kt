package com.lumecard.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.screens.deck.DeckViewModel
import com.lumecard.app.ui.screens.learningplan.LearningPlanViewModel
import com.lumecard.shared.model.Deck
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class StudyMode {
    MIXED,
    SINGLE,
    MULTI
}

class StudyModeScreen(
    private val planIds: List<String> = emptyList(),
    private val preSelectedDeckIds: List<String> = emptyList()
) : Screen {
    override val key: ScreenKey = "StudyMode_${planIds.sorted().joinToString("_")}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("OverloadResolutionAmbiguity")
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val deckViewModel: DeckViewModel = koinInject()
        val planViewModel: LearningPlanViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        val scope = rememberCoroutineScope()
        val decks by deckViewModel.decks.collectAsState()
        val deckCardCounts by deckViewModel.deckCardCounts.collectAsState()

        var selectedMode by remember { mutableStateOf(StudyMode.MIXED) }
        var selectedDeckIds by remember { mutableStateOf(setOf<String>()) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val studyableDecks = decks.filter { (deckCardCounts[it.id] ?: 0) > 0 }

        LaunchedEffect(Unit) {
            try {
                deckViewModel.loadDecks()
            } catch (e: Exception) {
                println("[LumeCard ERROR] StudyModeScreen loadDecks: ${e.message}")
                e.printStackTrace()
                errorMsg = "loadDecks: ${e.message}\n${e.stackTraceToString()}"
            }
        }

        LaunchedEffect(decks, planIds, preSelectedDeckIds) {
            if (planIds.isNotEmpty() && preSelectedDeckIds.isNotEmpty() && decks.isNotEmpty()) {
                val validIds = preSelectedDeckIds.filter { id -> decks.any { it.id == id } }
                if (validIds.isNotEmpty()) {
                    selectedDeckIds = validIds.toSet()
                    selectedMode = if (validIds.size == 1) StudyMode.SINGLE else StudyMode.MULTI
                }
            }
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
                        }, interactionSource = null) {
                            Text(strings.actionCopy)
                        }
                        Button(onClick = { errorMsg = null }, interactionSource = null) {
                            Text(strings.actionOk)
                        }
                    }
                },
            )
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.modeTitle,
                    onBack = { navigator.pop() }
                )
            },
            bottomBar = {
                val enabled = when (selectedMode) {
                    StudyMode.MIXED -> studyableDecks.isNotEmpty()
                    StudyMode.SINGLE -> selectedDeckIds.size == 1
                    StudyMode.MULTI -> selectedDeckIds.isNotEmpty()
                }
                val totalCards = when (selectedMode) {
                    StudyMode.MIXED -> studyableDecks.sumOf { deckCardCounts[it.id] ?: 0 }
                    StudyMode.SINGLE, StudyMode.MULTI -> selectedDeckIds.sumOf { deckCardCounts[it] ?: 0 }
                }
                Surface(
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LumeCardTheme.spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(LumeCardTheme.spacing.section),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            strings.modeCardsCount(totalCards),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (planIds.isNotEmpty() && selectedMode != StudyMode.MIXED) {
                            OutlinedButton(
                                onClick = {
                                    val deckIds = selectedDeckIds.toList()
                                    scope.launch {
                                        try {
                                            val plan = planViewModel.getPlanById(planIds.first())
                                            if (plan != null) {
                                                planViewModel.updatePlan(
                                                    id = plan.id,
                                                    name = plan.name,
                                                    description = plan.description,
                                                    knowledgeBaseIds = plan.knowledgeBaseIds,
                                                    deckIds = deckIds,
                                                    cardIds = plan.cardIds,
                                                    isDefault = plan.isDefault
                                                )
                                            }
                                        } catch (e: Exception) {
                                            println("[LumeCard ERROR] StudyModeScreen savePlan: ${e.message}")
                                        }
                                    }
                                },
                                interactionSource = null,
                                enabled = enabled
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.actionSave)
                            }
                        }
                        Button(
                            onClick = {
                                val deckIds = when (selectedMode) {
                                    StudyMode.MIXED -> studyableDecks.map { it.id }
                                    StudyMode.SINGLE, StudyMode.MULTI -> selectedDeckIds.toList()
                                }
                                val name = when (selectedMode) {
                                    StudyMode.MIXED -> strings.modeStartMixed
                                    StudyMode.SINGLE -> {
                                        val deck = studyableDecks.find { it.id in selectedDeckIds }
                                        deck?.name ?: strings.modeStartSingle
                                    }
                                    StudyMode.MULTI -> strings.modeStartMulti
                                }
                                scope.launch {
                                    try {
                                        val resolvedPlanIds = if (planIds.isEmpty() && deckIds.isNotEmpty()) {
                                            val plan = planViewModel.createPlan(
                                                name = strings.planAutoName(deckIds.size),
                                                description = null,
                                                knowledgeBaseIds = emptyList(),
                                                deckIds = deckIds,
                                                cardIds = emptyList(),
                                            )
                                            listOf(plan.id)
                                        } else planIds
                                        if (resolvedPlanIds.isNotEmpty() && selectedMode != StudyMode.MIXED) {
                                            val plan = planViewModel.getPlanById(resolvedPlanIds.first())
                                            if (plan != null) {
                                                planViewModel.updatePlan(
                                                    id = plan.id,
                                                    name = plan.name,
                                                    description = plan.description,
                                                    knowledgeBaseIds = plan.knowledgeBaseIds,
                                                    deckIds = deckIds,
                                                    cardIds = plan.cardIds,
                                                    isDefault = plan.isDefault
                                                )
                                            }
                                        }
                                        navigator.push(StudyScreen(deckIds, name, planIds = resolvedPlanIds))
                                    } catch (e: Exception) {
                                        println("[LumeCard ERROR] StudyModeScreen navigate: ${e.message}")
                                        e.printStackTrace()
                                        errorMsg = "navigate: ${e.message}\n${e.stackTraceToString()}"
                                    }
                                }
                            },
                            interactionSource = null,
                            enabled = enabled
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.modeStartLearning)
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.modeSelectMode,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(StudyMode.entries) { mode ->
                    val count = when (mode) {
                        StudyMode.MIXED -> studyableDecks.sumOf { deckCardCounts[it.id] ?: 0 }
                        StudyMode.SINGLE, StudyMode.MULTI -> 0
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedMode = mode
                            if (mode == StudyMode.SINGLE) {
                                selectedDeckIds = emptySet()
                            } else if (mode == StudyMode.MULTI) {
                                selectedDeckIds = emptySet()
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedMode == mode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMode == mode,
                                onClick = {
                                    selectedMode = mode
                                    if (mode == StudyMode.SINGLE) {
                                        selectedDeckIds = emptySet()
                                    } else if (mode == StudyMode.MULTI) {
                                        selectedDeckIds = emptySet()
                                    }
                                },
                                interactionSource = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            val label = when (mode) {
                                StudyMode.MIXED -> strings.modeMixed
                                StudyMode.SINGLE -> strings.modeSingle
                                StudyMode.MULTI -> strings.modeMulti
                            }
                            val description = when (mode) {
                                StudyMode.MIXED -> strings.modeMixedDesc
                                StudyMode.SINGLE -> strings.modeSingleDesc
                                StudyMode.MULTI -> strings.modeMultiDesc
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (mode == StudyMode.MIXED && count > 0) {
                                Text(
                                    strings.deckCardsCount(count),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (selectedMode == StudyMode.SINGLE || selectedMode == StudyMode.MULTI) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            strings.modeSelectDecks,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (studyableDecks.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        strings.modeNoDecks,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(studyableDecks, key = { it.id }) { deck ->
                            val cardCount = deckCardCounts[deck.id] ?: 0
                            val isSelected = deck.id in selectedDeckIds
                            val canSelect = if (selectedMode == StudyMode.SINGLE) !isSelected || selectedDeckIds.size <= 1 else true

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (selectedMode == StudyMode.SINGLE) {
                                        selectedDeckIds = setOf(deck.id)
                                    } else {
                                        selectedDeckIds = if (isSelected) {
                                            selectedDeckIds - deck.id
                                        } else {
                                            selectedDeckIds + deck.id
                                        }
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedMode == StudyMode.MULTI) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedDeckIds = if (checked) {
                                                    selectedDeckIds + deck.id
                                                } else {
                                                    selectedDeckIds - deck.id
                                                }
                                            },
                                            interactionSource = null,
                                        )
                                    } else {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedDeckIds = setOf(deck.id)
                                            },
                                            interactionSource = null,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(deck.icon, style = MaterialTheme.typography.headlineSmall)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(deck.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            strings.deckCardsCount(cardCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}



