package com.lumecard.app.ui.screens.learningplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTextField
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
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
        val radius = LumeCardTheme.radius

        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var isDefault by remember { mutableStateOf(false) }

        val knowledgeBases by viewModel.knowledgeBases.collectAsState()
        val decks by viewModel.decks.collectAsState()
        val cards by viewModel.cards.collectAsState()

        var selectedKbIds by remember { mutableStateOf(setOf<String>()) }
        var selectedDeckIds by remember { mutableStateOf(setOf<String>()) }
        var selectedCardIds by remember { mutableStateOf(setOf<String>()) }
        var expandedKbIds by remember { mutableStateOf(setOf<String>()) }
        var expandedDeckIds by remember { mutableStateOf(setOf<String>()) }

        var showSuccess by remember { mutableStateOf(false) }
        var showError by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(editPlanId) {
            if (editPlanId != null) {
                val plan = viewModel.plans.value.find { it.id == editPlanId }
                    ?: viewModel.getPlanById(editPlanId)
                if (plan != null) {
                    name = plan.name
                    description = plan.description ?: ""
                    isDefault = plan.isDefault
                    selectedKbIds = plan.knowledgeBaseIds.toSet()
                    selectedDeckIds = plan.deckIds.toSet()
                    selectedCardIds = plan.cardIds.toSet()
                }
            }
        }

        val decksByKb: Map<String, List<Deck>> = remember(decks) {
            decks.groupBy { it.knowledgeBaseId }
        }
        val cardsByDeck: Map<String, List<Card>> = remember(cards) {
            cards.groupBy { it.deckId }
        }
        val selectedCardCount = remember(
            selectedKbIds, selectedDeckIds, selectedCardIds, decksByKb, cardsByDeck
        ) {
            var count = 0
            for (kbId in selectedKbIds) {
                count += (decksByKb[kbId] ?: emptyList()).sumOf { deck ->
                    (cardsByDeck[deck.id] ?: emptyList()).size
                }
            }
            for (deckId in selectedDeckIds) {
                val coveredByKb = selectedKbIds.any { kbId ->
                    (decksByKb[kbId] ?: emptyList()).any { it.id == deckId }
                }
                if (!coveredByKb) {
                    count += (cardsByDeck[deckId] ?: emptyList()).size
                }
            }
            count + selectedCardIds.size
        }

        val canSave = name.isNotBlank()

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.planDefault, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }

                Spacer(Modifier.height(spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        strings.planSelectCardsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = radius.pill,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            strings.planCardsCount(selectedCardCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(knowledgeBases, key = { it.id }) { kb ->
                        KnowledgeBaseSelectorItem(
                            kb = kb,
                            decks = decksByKb[kb.id] ?: emptyList(),
                            cardsByDeck = cardsByDeck,
                            isExpanded = kb.id in expandedKbIds,
                            isSelected = kb.id in selectedKbIds,
                            onToggleExpanded = {
                                expandedKbIds = if (kb.id in expandedKbIds) expandedKbIds - kb.id
                                else expandedKbIds + kb.id
                            },
                            onToggleKb = { checked ->
                                selectedKbIds = if (checked) selectedKbIds + kb.id else selectedKbIds - kb.id
                            },
                            selectedDeckIds = selectedDeckIds,
                            onToggleDeck = { deckId, checked ->
                                selectedDeckIds = if (checked) selectedDeckIds + deckId else selectedDeckIds - deckId
                            },
                            selectedCardIds = selectedCardIds,
                            onToggleCard = { cardId, checked ->
                                selectedCardIds = if (checked) selectedCardIds + cardId else selectedCardIds - cardId
                            },
                            expandedDeckIds = expandedDeckIds,
                            onToggleDeckExpanded = { deckId ->
                                expandedDeckIds = if (deckId in expandedDeckIds) expandedDeckIds - deckId
                                else expandedDeckIds + deckId
                            },
                        )
                    }
                    item {
                        Spacer(Modifier.height(spacing.md))
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = try {
                                        if (editPlanId != null) {
                                            viewModel.updatePlan(
                                                id = editPlanId,
                                                name = name,
                                                description = description.ifBlank { null },
                                                knowledgeBaseIds = selectedKbIds.toList(),
                                                deckIds = selectedDeckIds.toList(),
                                                cardIds = selectedCardIds.toList(),
                                                isDefault = isDefault
                                            )
                                        } else {
                                            viewModel.createPlan(
                                                name = name,
                                                description = description.ifBlank { null },
                                                knowledgeBaseIds = selectedKbIds.toList(),
                                                deckIds = selectedDeckIds.toList(),
                                                cardIds = selectedCardIds.toList(),
                                                isDefault = isDefault
                                            )
                                        }
                                        true
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (result) showSuccess = true else showError = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canSave) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (canSave) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        ) {
                            Text(strings.actionSave)
                        }
                        Spacer(Modifier.height(spacing.md))
                    }
                }
            }
        }

        if (showSuccess) {
            AlertDialog(
                onDismissRequest = { showSuccess = false },
                title = { Text(if (editPlanId != null) strings.planUpdated else strings.planCreated) },
                text = { Text(if (editPlanId != null) strings.planSavedDescUpdate else strings.planSavedDescCreate) },
                confirmButton = {
                    Button(onClick = {
                        showSuccess = false
                        navigator.pop()
                    }) { Text(strings.actionOk) }
                }
            )
        }
        if (showError) {
            AlertDialog(
                onDismissRequest = { showError = false },
                title = { Text(strings.errorTitle) },
                text = { Text(strings.errorDesc) },
                confirmButton = { Button(onClick = { showError = false }) { Text(strings.actionOk) } }
            )
        }
    }
}

@Composable
private fun KnowledgeBaseSelectorItem(
    kb: KnowledgeBase,
    decks: List<Deck>,
    cardsByDeck: Map<String, List<Card>>,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleKb: (Boolean) -> Unit,
    selectedDeckIds: Set<String>,
    onToggleDeck: (String, Boolean) -> Unit,
    selectedCardIds: Set<String>,
    onToggleCard: (String, Boolean) -> Unit,
    expandedDeckIds: Set<String>,
    onToggleDeckExpanded: (String) -> Unit,
) {
    val strings = koinInject<I18nManager>().strings
    val spacing = LumeCardTheme.spacing
    val radius = LumeCardTheme.radius

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = radius.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onToggleKb,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = "${kb.icon} ${kb.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val kbCardCount = decks.sumOf { (cardsByDeck[it.id] ?: emptyList()).size }
                Text(
                    text = strings.planCardsCount(kbCardCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpanded) {
                decks.forEach { deck ->
                    DeckSelectorItem(
                        deck = deck,
                        cards = cardsByDeck[deck.id] ?: emptyList(),
                        isExpanded = deck.id in expandedDeckIds,
                        isSelected = deck.id in selectedDeckIds,
                        onToggleExpanded = { onToggleDeckExpanded(deck.id) },
                        onToggleDeck = { checked -> onToggleDeck(deck.id, checked) },
                        selectedCardIds = selectedCardIds,
                        onToggleCard = { cardId, checked -> onToggleCard(cardId, checked) },
                    )
                }
                if (decks.isEmpty()) {
                    Text(
                        text = strings.planNoDecksInKb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckSelectorItem(
    deck: Deck,
    cards: List<Card>,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleDeck: (Boolean) -> Unit,
    selectedCardIds: Set<String>,
    onToggleCard: (String, Boolean) -> Unit,
) {
    val strings = koinInject<I18nManager>().strings
    val spacing = LumeCardTheme.spacing

    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggleDeck,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = "${deck.icon} ${deck.name}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings.planCardsCount(cards.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isExpanded) {
            if (cards.isEmpty()) {
                Text(
                    text = strings.planNoCardsInDeck,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            } else {
                cards.forEach { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = card.id in selectedCardIds,
                            onCheckedChange = { checked -> onToggleCard(card.id, checked) },
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = card.title.ifBlank { card.front.take(40) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}