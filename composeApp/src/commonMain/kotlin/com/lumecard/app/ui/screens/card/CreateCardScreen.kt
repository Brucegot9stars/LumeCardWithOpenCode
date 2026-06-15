package com.lumecard.app.ui.screens.card

import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.components.MarkdownText
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.components.CardTypeSelector
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.ui.theme.LumeCardTheme
import org.koin.compose.koinInject

class CreateCardScreen(
    private val deckId: String,
    private val deckName: String,
    private val editCard: Card? = null
) : Screen {
    override val key: ScreenKey = "CreateCard_${editCard?.id ?: deckId}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: CardViewModel = koinInject()
        val strings = koinInject<I18nManager>().strings
        var front by remember { mutableStateOf(editCard?.front ?: "") }
        var back by remember { mutableStateOf(editCard?.back ?: "") }
        var cardType by remember { mutableStateOf(editCard?.type ?: CardType.BASIC) }
        var tags by remember { mutableStateOf(editCard?.tags?.joinToString(", ") ?: "") }
        var showTypeMenu by remember { mutableStateOf(false) }
        var showTypeHelp by remember { mutableStateOf(true) }
        val isEditing = editCard != null

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = if (isEditing) strings.cardEdit else strings.cardCreate,
                    onBack = { navigator.pop() },
                    action = {
                        IconButton(
                            onClick = {
                                if (isEditing) {
                                    viewModel.updateCard(
                                        card = editCard,
                                        front = front,
                                        back = back,
                                        type = cardType,
                                        tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    )
                                } else {
                                    viewModel.createCard(
                                        deckId = deckId,
                                        front = front,
                                        back = back,
                                        type = cardType,
                                        tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    )
                                }
                                navigator.pop()
                            },
                            enabled = front.isNotBlank() && back.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = strings.actionSave)
                        }
                    },
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(LumeCardTheme.spacing.md)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LumeCardTheme.radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        strings.deckNameLabel(deckName),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // 卡片类型选择
                Text(strings.cardType, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                CardTypeSelector(
                    selectedType = cardType,
                    onTypeSelected = { cardType = it },
                )

                // 类型专用输入区
                CardTypeInput(
                    type = cardType,
                    front = front,
                    onFrontChange = { front = it },
                    back = back,
                    onBackChange = { back = it }
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.cardTags) },
                    placeholder = { Text(strings.cardTagsPlaceholder) },
                    supportingText = { Text(strings.cardTagsHint) }
                )

                if (front.isNotBlank() || back.isNotBlank()) {
                    Text(strings.cardPreview, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(strings.cardQuestionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MarkdownText(markdown = front, modifier = Modifier.fillMaxWidth())
                            if (back.isNotBlank()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(strings.cardAnswerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MarkdownText(markdown = back, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.cardTypeHelp, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { showTypeHelp = !showTypeHelp }) {
                                Icon(
                                                    if (showTypeHelp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showTypeHelp) strings.cardCollapse else strings.cardExpand
                                )
                            }
                        }
                        AnimatedVisibility(visible = showTypeHelp) {
                            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                Text(typeHelpText(cardType, strings), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(strings.cardExample, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(typeExampleText(cardType, strings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CardTypeInput(
    type: CardType,
    front: String,
    onFrontChange: (String) -> Unit,
    back: String,
    onBackChange: (String) -> Unit
) {
    val strings = koinInject<I18nManager>().strings
    when (type) {
        CardType.BASIC, CardType.REVERSED, CardType.MARKDOWN, CardType.AI_GENERATED -> {
            BasicCardFields(
                front = front, onFrontChange = onFrontChange,
                back = back, onBackChange = onBackChange,
                frontLabel = if (type == CardType.REVERSED) strings.cardFrontLabelRev else strings.cardFrontLabel,
                backLabel = if (type == CardType.REVERSED) strings.cardBackLabelRev else strings.cardBackLabel,
                frontPlaceholder = strings.cardFrontPlaceholder,
                backPlaceholder = strings.cardBackPlaceholder
            )
        }
        CardType.CLOZE -> {
            Text(strings.cardClozeContent, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(strings.cardClozeFormatHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text(strings.cardClozePlaceholder) }
            )
            Text(strings.cardClozeFullText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text(strings.cardClozeBackPlaceholder) }
            )
        }
        CardType.MULTIPLE_CHOICE -> {
            Text(strings.cardChoiceQuestion, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                placeholder = { Text(strings.cardChoiceQuestionPlaceholder) }
            )
            Text(strings.cardChoiceOptions, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(strings.cardChoiceFormatHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text(strings.cardChoicePlaceholder) }
            )
        }
        CardType.IMAGE_OCCLUSION -> {
            Text(strings.cardOcclusionImage, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(strings.cardOcclusionImagePlaceholder) },
                supportingText = { Text(strings.cardOcclusionImageHint) }
            )
            Text(strings.cardOcclusionContent, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text(strings.cardOcclusionContentPlaceholder) }
            )
        }
        CardType.AUDIO -> {
            Text(strings.cardAudioRef, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(strings.cardAudioPlaceholder) },
                supportingText = { Text(strings.cardAudioHint) }
            )
            Text(strings.cardAudioContent, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text(strings.cardAudioContentPlaceholder) }
            )
        }
        CardType.VIDEO -> {
            Text(strings.cardVideoRef, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(strings.cardVideoPlaceholder) },
                supportingText = { Text(strings.cardVideoHint) }
            )
            Text(strings.cardVideoContent, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text(strings.cardVideoContentPlaceholder) }
            )
        }
    }
}

@Composable
private fun BasicCardFields(
    front: String, onFrontChange: (String) -> Unit,
    back: String, onBackChange: (String) -> Unit,
    frontLabel: String, backLabel: String,
    frontPlaceholder: String, backPlaceholder: String
) {
    val strings = koinInject<I18nManager>().strings
    Text(frontLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = front,
        onValueChange = onFrontChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
        placeholder = { Text(frontPlaceholder) },
        supportingText = { Text(strings.noteMarkdownSupport) }
    )
    Text(backLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = back,
        onValueChange = onBackChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
        placeholder = { Text(backPlaceholder) },
        supportingText = { Text(strings.noteMarkdownSupport) }
    )
}

private fun cardTypeLabel(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasic
    CardType.REVERSED -> strings.cardTypeReversed
    CardType.CLOZE -> strings.cardTypeCloze
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoice
    CardType.IMAGE_OCCLUSION -> strings.cardTypeOcclusion
    CardType.AUDIO -> strings.cardTypeAudio
    CardType.VIDEO -> strings.cardTypeVideo
    CardType.MARKDOWN -> strings.cardTypeMarkdown
    CardType.AI_GENERATED -> strings.cardTypeAi
}

private fun cardTypeDesc(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasicDesc
    CardType.REVERSED -> strings.cardTypeReversedDesc
    CardType.CLOZE -> strings.cardTypeClozeDesc
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoiceDesc
    CardType.IMAGE_OCCLUSION -> strings.cardTypeOcclusionDesc
    CardType.AUDIO -> strings.cardTypeAudioDesc
    CardType.VIDEO -> strings.cardTypeVideoDesc
    CardType.MARKDOWN -> strings.cardTypeMarkdownDesc
    CardType.AI_GENERATED -> strings.cardTypeAiDesc
}

private fun typeHelpText(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasicHelp
    CardType.REVERSED -> strings.cardTypeReversedHelp
    CardType.CLOZE -> strings.cardTypeClozeHelp
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoiceHelp
    CardType.IMAGE_OCCLUSION -> strings.cardTypeOcclusionHelp
    CardType.AUDIO -> strings.cardTypeAudioHelp
    CardType.VIDEO -> strings.cardTypeVideoHelp
    CardType.MARKDOWN -> strings.cardTypeMarkdownHelp
    CardType.AI_GENERATED -> strings.cardTypeAiHelp
}

private fun typeExampleText(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasicExample
    CardType.REVERSED -> strings.cardTypeReversedExample
    CardType.CLOZE -> strings.cardTypeClozeExample
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoiceExample
    CardType.IMAGE_OCCLUSION -> strings.cardTypeOcclusionExample
    CardType.AUDIO -> strings.cardTypeAudioExample
    CardType.VIDEO -> strings.cardTypeVideoExample
    CardType.MARKDOWN -> strings.cardTypeMarkdownExample
    CardType.AI_GENERATED -> strings.cardTypeAiExample
}





