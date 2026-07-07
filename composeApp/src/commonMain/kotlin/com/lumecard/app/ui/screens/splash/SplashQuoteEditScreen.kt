package com.lumecard.app.ui.screens.splash

import androidx.compose.foundation.clickable
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.app.platform.pickOpenFile
import com.lumecard.shared.data.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class SplashQuoteEditScreen(
    private val quoteIndex: Int,
    private val initialText: String = "",
    private val initialAuthor: String = "",
    private val initialOverrideJson: String? = null,
) : Screen {
    override val key: ScreenKey = "SplashQuoteEdit_$quoteIndex"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val strings = koinInject<I18nManager>().strings
        val vm: SplashQuoteViewModel = koinInject()
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val json = remember { Json { ignoreUnknownKeys = true } }

        val initialOverride = remember(initialOverrideJson) {
            initialOverrideJson?.let { try { json.decodeFromString<QuoteOverrideConfig>(it) } catch (_: Exception) { null } }
        }

        // ── Core fields ─────────────────────────────────
        var text by remember { mutableStateOf(initialText) }
        var author by remember { mutableStateOf(initialAuthor) }

        // ── Direction override ─────────────────────────
        var dirOverrideEnabled by remember { mutableStateOf(initialOverride?.direction != null) }
        var dirValue by remember { mutableStateOf(initialOverride?.direction ?: SplashQuoteDirection.HORIZONTAL) }

        // ── Font override ──────────────────────────────
        var fontOverrideEnabled by remember { mutableStateOf(initialOverride?.font != null) }
        var fontIdValue by remember { mutableStateOf(initialOverride?.font ?: "") }
        var fontSizeOverrideEnabled by remember { mutableStateOf(initialOverride?.fontSize != null) }
        var fontSizeValue by remember { mutableStateOf(initialOverride?.fontSize ?: 24f) }

        // ── Show author override ───────────────────────
        var showAuthorOverrideEnabled by remember { mutableStateOf(initialOverride?.showAuthor != null) }
        var showAuthorValue by remember { mutableStateOf(initialOverride?.showAuthor ?: true) }

        // ── Background override ────────────────────────
        var bgType by remember { mutableStateOf(initialOverride?.background?.type ?: BackgroundType.FOLLOW_GLOBAL) }
        var bgColor by remember { mutableStateOf(initialOverride?.background?.color ?: "") }
        var bgImagePath by remember { mutableStateOf(initialOverride?.background?.imagePath ?: "") }

        // ── Layout override ───────────────────────────
        var layoutEnabled by remember { mutableStateOf(initialOverride?.layout != null) }
        var textAlignValue by remember { mutableStateOf(initialOverride?.layout?.textAlign ?: TextAlignOverride.CENTER) }
        var authorAlignValue by remember { mutableStateOf(initialOverride?.layout?.authorAlign ?: TextAlignOverride.CENTER) }
        var contentSpacingValue by remember { mutableStateOf(initialOverride?.layout?.contentSpacing ?: 24f) }
        var pagePaddingValue by remember { mutableStateOf(initialOverride?.layout?.pagePadding ?: 32f) }
        var maxWidthValue by remember { mutableStateOf(initialOverride?.layout?.maxWidth ?: 0.8f) }
        var verticalPosValue by remember { mutableStateOf(initialOverride?.layout?.verticalPosition ?: VerticalPosition.CENTER) }

        // ── Inherit global defaults ────────────────────
        val quoteManager: SplashQuoteManager = koinInject()
        LaunchedEffect(Unit) {
            val global = quoteManager.loadSettings()
            if (initialOverride?.direction == null) dirValue = global.direction
            if (initialOverride?.font == null) fontIdValue = global.font
            if (initialOverride?.fontSize == null && global.fontSize > 0f) fontSizeValue = global.fontSize
            if (initialOverride?.showAuthor == null) showAuthorValue = global.showAuthor
        }

        // ── Dirty tracking ─────────────────────────────
        fun computeIsDirty(): Boolean {
            val current = buildOverride(layoutEnabled, dirOverrideEnabled, dirValue,
                fontOverrideEnabled, fontIdValue, fontSizeOverrideEnabled, fontSizeValue,
                showAuthorOverrideEnabled, showAuthorValue,
                bgType, bgColor, bgImagePath,
                textAlignValue, authorAlignValue, contentSpacingValue, pagePaddingValue, maxWidthValue, verticalPosValue)
            val overrideChanged = current != initialOverride
            return text != initialText ||
                author != initialAuthor ||
                overrideChanged
        }
        var isDirty by remember { mutableStateOf(false) }
        val updateDirty = { isDirty = computeIsDirty() }

                        // ── Preview state ──────────────────────────────
                        var showPreview by remember { mutableStateOf(false) }

                        // ── Discard confirmation ───────────────────────
                        var showDiscardDialog by remember { mutableStateOf(false) }

                        // ── Coroutine scope for save ───────────────────
                        val saveScope = rememberCoroutineScope()

                        // ── Save ────────────────────────────────────────
                        fun buildFinalQuote(): Pair<SplashQuoteData, Boolean> {
            val hasOverride = layoutEnabled || dirOverrideEnabled || fontOverrideEnabled ||
                fontSizeOverrideEnabled || showAuthorOverrideEnabled || bgType != BackgroundType.FOLLOW_GLOBAL
            val override = if (hasOverride) {
                QuoteOverrideConfig(
                    direction = if (dirOverrideEnabled) dirValue else null,
                    font = if (fontOverrideEnabled) fontIdValue.ifBlank { null } else null,
                    fontSize = if (fontSizeOverrideEnabled) fontSizeValue else null,
                    showAuthor = if (showAuthorOverrideEnabled) showAuthorValue else null,
                    background = if (bgType != BackgroundType.FOLLOW_GLOBAL) {
                        BackgroundOverride(type = bgType, color = bgColor.ifBlank { null }, imagePath = bgImagePath.ifBlank { null })
                    } else null,
                    layout = if (layoutEnabled) {
                        QuoteLayoutOverride(
                            textAlign = textAlignValue,
                            authorAlign = authorAlignValue,
                            contentSpacing = contentSpacingValue,
                            pagePadding = pagePaddingValue,
                            maxWidth = maxWidthValue,
                            verticalPosition = verticalPosValue,
                        )
                    } else null,
                )
            } else null
            val quote = SplashQuoteData(text = text.trim(), author = author.trim(), overrideConfig = override)
            return quote to hasOverride
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = if (quoteIndex < 0) strings.splashQuoteAdd else strings.splashQuoteEdit,
                    onBack = {
                        if (isDirty) showDiscardDialog = true
                        else navigator.pop()
                    },
                    action = {
                        FilledTonalButton(
                            onClick = {
                                val (quote, _) = buildFinalQuote()
                                saveScope.launch {
                                    vm.saveQuote(quoteIndex, quote)
                                    navigator.pop()
                                }
                            },
                            enabled = text.isNotBlank(),
                        ) {
                            Text(strings.actionSave)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.section),
            ) {
                Spacer(Modifier.height(spacing.xs))

                // ── 1. Quote text ────────────────────────
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; updateDirty() },
                    label = { Text(strings.splashQuoteTextLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it; updateDirty() },
                    label = { Text(strings.splashQuoteAuthorLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // ── 2. Direction override ────────────────
                SectionHeader(strings.splashQuoteDirection)
                OverrideCard {
                    OverrideToggle(checked = dirOverrideEnabled, strings = strings) { dirOverrideEnabled = it; updateDirty() }
                    if (dirOverrideEnabled) {
                        DirectionSelector(
                            selected = dirValue,
                            strings = strings,
                            onSelect = { dirValue = it; updateDirty() },
                        )
                    }
                }

                // ── 3. Font override ──────────────────────
                SectionHeader(strings.splashQuoteFont)
                OverrideCard {
                    OverrideToggle(checked = fontOverrideEnabled, strings = strings) { fontOverrideEnabled = it; updateDirty() }
                    if (fontOverrideEnabled) {
                        FontSelector(
                            selectedFontId = fontIdValue,
                            strings = strings,
                            onSelect = { fontIdValue = it; updateDirty() },
                        )
                    }
                }

                SectionHeader(strings.splashQuoteFontSize)
                OverrideCard {
                    OverrideToggle(checked = fontSizeOverrideEnabled, strings = strings) { fontSizeOverrideEnabled = it; updateDirty() }
                    if (fontSizeOverrideEnabled) {
                        FontSizeStepper(
                            value = fontSizeValue,
                            strings = strings,
                            onValueChange = { fontSizeValue = it.coerceIn(12f, 72f); updateDirty() },
                        )
                    }
                }

                // ── 4. Show author override ──────────────
                SectionHeader(strings.splashQuoteShowAuthor)
                OverrideCard {
                    OverrideToggle(checked = showAuthorOverrideEnabled, strings = strings) { showAuthorOverrideEnabled = it; updateDirty() }
                    if (showAuthorOverrideEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                            Text(strings.splashQuoteShowAuthor)
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = showAuthorValue, onCheckedChange = { showAuthorValue = it; updateDirty() })
                        }
                    }
                }

                // ── 5. Background override ───────────────
                SectionHeader(strings.splashQuoteBackground)
                OverrideCard {
                    OverrideToggle(
                        checked = bgType != BackgroundType.FOLLOW_GLOBAL,
                        strings = strings,
                        onCheckedChange = {
                            bgType = if (it) BackgroundType.SOLID_COLOR else BackgroundType.FOLLOW_GLOBAL
                            updateDirty()
                        },
                    )
                    if (bgType != BackgroundType.FOLLOW_GLOBAL) {
                        Column(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = bgType == BackgroundType.SOLID_COLOR,
                                    onClick = { bgType = BackgroundType.SOLID_COLOR; updateDirty() },
                                    label = { Text(strings.splashQuoteBgSolidColor) },
                                )
                                FilterChip(
                                    selected = bgType == BackgroundType.IMAGE,
                                    onClick = { bgType = BackgroundType.IMAGE; updateDirty() },
                                    label = { Text(strings.splashQuoteBgImage) },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            when (bgType) {
                                BackgroundType.SOLID_COLOR -> {
                                    OutlinedTextField(
                                        value = bgColor,
                                        onValueChange = { bgColor = it; updateDirty() },
                                        label = { Text(strings.splashQuoteBgColorLabel) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                BackgroundType.IMAGE -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = bgImagePath,
                                            onValueChange = { bgImagePath = it; updateDirty() },
                                            label = { Text(strings.splashQuoteBgImageLabel) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        val scope = rememberCoroutineScope()
                                        IconButton(onClick = {
                                            scope.launch {
                                                val path = withContext(Dispatchers.IO) { pickOpenFile("image/*") }
                                                if (path != null) { bgImagePath = path; updateDirty() }
                                            }
                                        }) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = strings.splashQuoteBrowse)
                                        }
                                    }
                                }
                                BackgroundType.FOLLOW_GLOBAL -> {}
                            }
                        }
                    }
                }

                // ── 6. Layout override ────────────────────
                SectionHeader(strings.splashQuoteLayout)
                OverrideCard {
                    OverrideToggle(checked = layoutEnabled, strings = strings) { layoutEnabled = it; updateDirty() }
                    if (layoutEnabled) {
                        Column(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                            Text(strings.splashQuoteTextAlign, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextAlignOverride.entries.forEach { a ->
                                    FilterChip(
                                        selected = textAlignValue == a,
                                        onClick = { textAlignValue = a; updateDirty() },
                                        label = { Text(a.name, style = MaterialTheme.typography.bodySmall) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            Text(strings.splashQuoteAuthorAlign, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextAlignOverride.entries.forEach { a ->
                                    FilterChip(
                                        selected = authorAlignValue == a,
                                        onClick = { authorAlignValue = a; updateDirty() },
                                        label = { Text(a.name, style = MaterialTheme.typography.bodySmall) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            Text(strings.splashQuoteVerticalPosition, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                VerticalPosition.entries.forEach { p ->
                                    FilterChip(
                                        selected = verticalPosValue == p,
                                        onClick = { verticalPosValue = p; updateDirty() },
                                        label = { Text(p.name, style = MaterialTheme.typography.bodySmall) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            LabeledSlider(
                                label = strings.splashQuoteContentSpacing(contentSpacingValue.toInt()),
                                value = contentSpacingValue,
                                onValueChange = { contentSpacingValue = it; updateDirty() },
                                valueRange = 4f..64f,
                            )
                            LabeledSlider(
                                label = strings.splashQuotePagePadding(pagePaddingValue.toInt()),
                                value = pagePaddingValue,
                                onValueChange = { pagePaddingValue = it; updateDirty() },
                                valueRange = 8f..80f,
                            )
                            LabeledSlider(
                                label = strings.splashQuoteMaxWidth((maxWidthValue * 100).toInt()),
                                value = maxWidthValue,
                                onValueChange = { maxWidthValue = it; updateDirty() },
                                valueRange = 0.3f..1.0f,
                            )
                        }
                    }
                }

                // ── 7. Preview ────────────────────────────
                Button(
                    onClick = { showPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.splashQuotePreview)
                }

                Spacer(Modifier.height(spacing.lg))
            }
        }

        // ── Discard confirmation ────────────────────────
        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text(strings.splashQuoteUnsavedTitle) },
                text = { Text(strings.splashQuoteUnsavedDesc) },
                confirmButton = {
                    Button(onClick = { showDiscardDialog = false; navigator.pop() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text(strings.splashQuoteDiscard)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text(strings.actionCancel) }
                },
            )
        }

        // ── Preview overlay ────────────────────────────
        if (showPreview) {
            val previewOverride = buildOverride(layoutEnabled, dirOverrideEnabled, dirValue,
                fontOverrideEnabled, fontIdValue, fontSizeOverrideEnabled, fontSizeValue,
                showAuthorOverrideEnabled, showAuthorValue,
                bgType, bgColor, bgImagePath,
                textAlignValue, authorAlignValue, contentSpacingValue, pagePaddingValue, maxWidthValue, verticalPosValue)
            SplashQuoteScreen(
                quote = SplashQuoteData(text = text, author = author),
                direction = SplashQuoteDirection.HORIZONTAL,
                splashFontId = "",
                splashFontSize = 0f,
                showAuthor = true,
                overrideConfig = previewOverride,
                onDismiss = { showPreview = false },
            )
        }
    }
}

// ── Helper composables ──────────────────────────────────────

@Composable
private fun OverrideCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) { Column(content = content) }
}

@Composable
private fun OverrideToggle(checked: Boolean, strings: com.lumecard.app.i18n.I18nStrings, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(if (checked) strings.splashQuoteOverrideEnabled else strings.splashQuoteOverrideGlobal) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun DirectionSelector(
    selected: SplashQuoteDirection,
    strings: com.lumecard.app.i18n.I18nStrings,
    onSelect: (SplashQuoteDirection) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SplashQuoteDirection.entries.forEach { dir ->
            FilterChip(
                selected = selected == dir,
                onClick = { onSelect(dir) },
                label = {
                    Text(
                        when (dir) {
                            SplashQuoteDirection.HORIZONTAL -> strings.splashQuoteDirectionHorizontal
                            SplashQuoteDirection.VERTICAL -> strings.splashQuoteDirectionVertical
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun FontSelector(selectedFontId: String, strings: com.lumecard.app.i18n.I18nStrings, onSelect: (String) -> Unit) {
    val allFonts = FontRegistry.fonts
    var expanded by remember { mutableStateOf(false) }
    val displayName = FontRegistry.findById(selectedFontId)?.displayName ?: strings.splashQuoteFontDefault
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(displayName)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(strings.splashQuoteFontDefault) }, onClick = { onSelect(""); expanded = false })
            allFonts.forEach { spec ->
                DropdownMenuItem(
                    text = { Text(spec.displayName) },
                    onClick = { onSelect(spec.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun FontSizeStepper(value: Float, strings: com.lumecard.app.i18n.I18nStrings, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = { onValueChange(value - 2f) }, modifier = Modifier.size(32.dp)) {
            Text("-", style = MaterialTheme.typography.titleMedium)
        }
        Text(strings.splashQuoteFontSizeValue(value.toInt()), modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
        FilledIconButton(onClick = { onValueChange(value + 2f) }, modifier = Modifier.size(32.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Helper ──────────────────────────────────────────────────

private fun buildOverride(
    layoutEnabled: Boolean, dirOverrideEnabled: Boolean, dirValue: SplashQuoteDirection,
    fontOverrideEnabled: Boolean, fontIdValue: String, fontSizeOverrideEnabled: Boolean, fontSizeValue: Float,
    showAuthorOverrideEnabled: Boolean, showAuthorValue: Boolean,
    bgType: BackgroundType, bgColor: String, bgImagePath: String,
    textAlignValue: TextAlignOverride, authorAlignValue: TextAlignOverride, contentSpacingValue: Float,
    pagePaddingValue: Float, maxWidthValue: Float, verticalPosValue: VerticalPosition,
): QuoteOverrideConfig? {
    val hasOverride = layoutEnabled || dirOverrideEnabled || fontOverrideEnabled ||
        fontSizeOverrideEnabled || showAuthorOverrideEnabled || bgType != BackgroundType.FOLLOW_GLOBAL
    if (!hasOverride) return null
    return QuoteOverrideConfig(
        direction = if (dirOverrideEnabled) dirValue else null,
        font = if (fontOverrideEnabled) fontIdValue.ifBlank { null } else null,
        fontSize = if (fontSizeOverrideEnabled) fontSizeValue else null,
        showAuthor = if (showAuthorOverrideEnabled) showAuthorValue else null,
        background = if (bgType != BackgroundType.FOLLOW_GLOBAL) {
            BackgroundOverride(type = bgType, color = bgColor.ifBlank { null }, imagePath = bgImagePath.ifBlank { null })
        } else null,
        layout = if (layoutEnabled) {
            QuoteLayoutOverride(
                textAlign = textAlignValue,
                authorAlign = authorAlignValue,
                contentSpacing = contentSpacingValue,
                pagePadding = pagePaddingValue,
                maxWidth = maxWidthValue,
                verticalPosition = verticalPosValue,
            )
        } else null,
    )
}

@Composable
private fun QuoteOverrideConfig?.equals(rhs: QuoteOverrideConfig?): Boolean {
    return when {
        this == null && rhs == null -> true
        this == null || rhs == null -> false
        else -> this.direction == rhs.direction && this.font == rhs.font &&
            this.fontSize == rhs.fontSize && this.showAuthor == rhs.showAuthor &&
            this.background?.type == rhs.background?.type && this.background?.color == rhs.background?.color &&
            this.background?.imagePath == rhs.background?.imagePath &&
            this.layout == rhs.layout
    }
}
