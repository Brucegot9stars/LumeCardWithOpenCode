package com.lumecard.app.ui.screens.card

import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.font.FontSpec
import com.lumecard.app.ui.components.RichTextCardEditor
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
        var horizontalCenter by remember { mutableStateOf(editCard?.metadata?.get("hcenter")?.toBoolean() ?: false) }
        var verticalCenter by remember { mutableStateOf(editCard?.metadata?.get("vcenter")?.toBoolean() ?: false) }
        var fontSize by remember { mutableStateOf(editCard?.metadata?.get("fontSize")?.toIntOrNull() ?: 16) }
        var fontFamily by remember { mutableStateOf(editCard?.metadata?.get("fontFamily") ?: "") }
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
                                val saveFront = front
                                val saveBack = back
                                if (isEditing) {
                                    viewModel.updateCard(
                                        card = editCard,
                                        front = saveFront,
                                        back = saveBack,
                                        type = cardType,
                                        tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                        horizontalCenter = horizontalCenter,
                                        verticalCenter = verticalCenter,
                                        fontSize = fontSize,
                                        fontFamily = fontFamily,
                                    )
                                } else {
                                    viewModel.createCard(
                                        deckId = deckId,
                                        front = saveFront,
                                        back = saveBack,
                                        type = cardType,
                                        tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                        horizontalCenter = horizontalCenter,
                                        verticalCenter = verticalCenter,
                                        fontSize = fontSize,
                                        fontFamily = fontFamily,
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
                    strings = strings,
                )

                // 类型专用输入区
                CardTypeInput(
                    type = cardType,
                    front = front,
                    onFrontChange = { front = it },
                    back = back,
                    onBackChange = { back = it },
                    horizontalCenter = horizontalCenter,
                    verticalCenter = verticalCenter,
                    onHorizontalCenterChange = { horizontalCenter = it },
                    onVerticalCenterChange = { verticalCenter = it },
                    fontSize = fontSize,
                    onFontSizeChange = { fontSize = it },
                    fontFamily = fontFamily,
                    onFontFamilyChange = { fontFamily = it },
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
                            CardPreviewContent(text = front, cardType = cardType, fontSize = fontSize)
                            if (back.isNotBlank()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(strings.cardAnswerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CardPreviewContent(text = back, cardType = cardType, fontSize = fontSize)
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

private fun clozeAutoBack(front: String): String {
    val clozeRegex = Regex("\\{\\{c\\d+::([^}]+)\\}\\}")
    val clozeHintRegex = Regex("\\{\\{c\\d+::([^}]+)::([^}]+)\\}\\}")
    return front.replace(clozeHintRegex, "$1").replace(clozeRegex, "$1")
}

@Composable
private fun ClozeQuickInsertButtons(
    text: String,
    onInsert: (String) -> Unit,
) {
    val clozeNumRegex = remember { Regex("\\{\\{c(\\d+)::") }
    val existingNumbers = remember(text) {
        clozeNumRegex.findAll(text).map { it.groupValues[1].toInt() }.toSet()
    }
    val maxExisting = existingNumbers.maxOrNull() ?: 0
    val maxButton = (maxExisting + 5).coerceIn(3, 9)

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..maxButton).forEach { n ->
            val isUsed = n in existingNumbers
            FilterChip(
                selected = isUsed,
                onClick = {
                    if (!isUsed) {
                        onInsert("{{c${n}::}}")
                    }
                },
                label = { Text("c$n", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun CardTypeInput(
    type: CardType,
    front: String,
    onFrontChange: (String) -> Unit,
    back: String,
    onBackChange: (String) -> Unit,
    horizontalCenter: Boolean = false,
    verticalCenter: Boolean = false,
    onHorizontalCenterChange: ((Boolean) -> Unit)? = null,
    onVerticalCenterChange: ((Boolean) -> Unit)? = null,
    fontSize: Int = 16,
    onFontSizeChange: ((Int) -> Unit)? = null,
    fontFamily: String = "",
    onFontFamilyChange: ((String) -> Unit)? = null,
) {
    val strings = koinInject<I18nManager>().strings
    when (type) {
        CardType.BASIC, CardType.REVERSED -> {
            BasicCardFields(
                front = front, onFrontChange = onFrontChange,
                back = back, onBackChange = onBackChange,
                frontLabel = if (type == CardType.REVERSED) strings.cardFrontLabelRev else strings.cardFrontLabel,
                backLabel = if (type == CardType.REVERSED) strings.cardBackLabelRev else strings.cardBackLabel,
                frontPlaceholder = strings.cardFrontPlaceholder,
                backPlaceholder = strings.cardBackPlaceholder,
                horizontalCenter = horizontalCenter,
                verticalCenter = verticalCenter,
                onHorizontalCenterChange = onHorizontalCenterChange,
                onVerticalCenterChange = onVerticalCenterChange,
                fontSize = fontSize,
                onFontSizeChange = onFontSizeChange,
                fontFamily = fontFamily,
                onFontFamilyChange = onFontFamilyChange,
            )
        }
        CardType.RICH_TEXT -> {
            RichTextCardEditor(
                front = front, onFrontChange = onFrontChange,
                back = back, onBackChange = onBackChange,
                frontLabel = strings.cardFrontLabel,
                backLabel = strings.cardBackLabel,
            )
        }
        CardType.MARKDOWN, CardType.AI_GENERATED -> {
            BasicCardFields(
                front = front, onFrontChange = onFrontChange,
                back = back, onBackChange = onBackChange,
                frontLabel = strings.cardFrontLabel,
                backLabel = strings.cardBackLabel,
                frontPlaceholder = strings.cardFrontPlaceholder,
                backPlaceholder = strings.cardBackPlaceholder
            )
        }
        CardType.CLOZE -> {
            Text(strings.cardClozeContent, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(strings.cardClozeFormatHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val onClozeChange = { newFront: String ->
                onFrontChange(newFront)
                onBackChange(clozeAutoBack(newFront))
            }
            ClozeQuickInsertButtons(
                text = front,
                onInsert = { marker -> onClozeChange(front + marker) }
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = front,
                onValueChange = onClozeChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text(strings.cardClozePlaceholder) }
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
    }
}

@Composable
private fun BasicCardFields(
    front: String, onFrontChange: (String) -> Unit,
    back: String, onBackChange: (String) -> Unit,
    frontLabel: String, backLabel: String,
    frontPlaceholder: String, backPlaceholder: String,
    horizontalCenter: Boolean = false,
    verticalCenter: Boolean = false,
    onHorizontalCenterChange: ((Boolean) -> Unit)? = null,
    onVerticalCenterChange: ((Boolean) -> Unit)? = null,
    fontSize: Int = 16,
    onFontSizeChange: ((Int) -> Unit)? = null,
    fontFamily: String = "",
    onFontFamilyChange: ((String) -> Unit)? = null,
) {
    val strings = koinInject<I18nManager>().strings
    Text(frontLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = front,
        onValueChange = onFrontChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
        placeholder = { Text(frontPlaceholder) }
    )
    Text(backLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = back,
        onValueChange = onBackChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
        placeholder = { Text(backPlaceholder) }
    )
    if (onHorizontalCenterChange != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.cardHorizontalCenter, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = horizontalCenter, onCheckedChange = onHorizontalCenterChange)
        }
    }
    if (onVerticalCenterChange != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.cardVerticalCenter, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = verticalCenter, onCheckedChange = onVerticalCenterChange)
        }
    }
    if (onFontSizeChange != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.cardFontSize, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { onFontSizeChange?.invoke(it.roundToInt()) },
                valueRange = 12f..120f,
                steps = 107,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("${fontSize}sp", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (onFontFamilyChange != null) {
        val fontOptions = remember { FontRegistry.fonts }
        var expanded by remember { mutableStateOf(false) }
        val selectedFont = fontOptions.find { it.id == fontFamily }
        val selectedLabel = selectedFont?.displayName ?: fontOptions.firstOrNull()?.displayName ?: "Default"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.cardFont, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(selectedLabel)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    fontOptions.forEach { spec ->
                        DropdownMenuItem(
                            text = {
                                val prefix = when (spec.source) {
                                    com.lumecard.app.font.FontSource.SYSTEM -> ""
                                    com.lumecard.app.font.FontSource.BUNDLED -> ""
                                    com.lumecard.app.font.FontSource.USER_IMPORTED -> ""
                                }
                                Text("$prefix${spec.displayName}")
                            },
                            onClick = { onFontFamilyChange(spec.id); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPreviewContent(text: String, cardType: CardType, fontSize: Int = 16) {
    when (cardType) {
        CardType.BASIC, CardType.REVERSED -> Text(text, modifier = Modifier.fillMaxWidth(), fontSize = fontSize.sp)
        else -> MarkdownText(markdown = text, modifier = Modifier.fillMaxWidth())
    }
}

private fun cardTypeLabel(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasic
    CardType.REVERSED -> strings.cardTypeReversed
    CardType.CLOZE -> strings.cardTypeCloze
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoice
    CardType.MARKDOWN -> strings.cardTypeMarkdown
    CardType.AI_GENERATED -> strings.cardTypeAi
    CardType.RICH_TEXT -> strings.cardTypeRichText
}

private fun cardTypeDesc(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasicDesc
    CardType.REVERSED -> strings.cardTypeReversedDesc
    CardType.CLOZE -> strings.cardTypeClozeDesc
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoiceDesc
    CardType.MARKDOWN -> strings.cardTypeMarkdownDesc
    CardType.AI_GENERATED -> strings.cardTypeAiDesc
    CardType.RICH_TEXT -> strings.cardTypeRichTextDesc
}

private fun typeHelpText(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasicHelp
    CardType.REVERSED -> strings.cardTypeReversedHelp
    CardType.CLOZE -> strings.cardTypeClozeHelp
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoiceHelp
    CardType.MARKDOWN -> strings.cardTypeMarkdownHelp
    CardType.AI_GENERATED -> strings.cardTypeAiHelp
    CardType.RICH_TEXT -> strings.cardTypeRichTextHelp
}

private fun typeExampleText(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasicExample
    CardType.REVERSED -> strings.cardTypeReversedExample
    CardType.CLOZE -> strings.cardTypeClozeExample
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoiceExample
    CardType.MARKDOWN -> strings.cardTypeMarkdownExample
    CardType.AI_GENERATED -> strings.cardTypeAiExample
    CardType.RICH_TEXT -> strings.cardTypeRichTextExample
}





