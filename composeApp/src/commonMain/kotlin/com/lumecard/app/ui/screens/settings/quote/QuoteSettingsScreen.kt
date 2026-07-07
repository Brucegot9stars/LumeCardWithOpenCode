package com.lumecard.app.ui.screens.settings.quote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.ui.components.ConfigSection
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.screens.splash.SplashQuoteListScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.SplashQuoteDirection
import com.lumecard.shared.data.SplashQuoteStrategy
import com.lumecard.shared.feature.quote.config.QuoteAnimationStyle
import com.lumecard.shared.feature.quote.config.QuoteDisplayConfig
import com.lumecard.app.feature.quote.viewer.QuoteViewer
import com.lumecard.app.platform.pickOpenFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class QuoteSettingsScreen : Screen {
    override val key: ScreenKey = "QuoteSettings"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val strings = koinInject<I18nManager>().strings
        val vm: QuoteSettingsViewModel = koinInject()
        val settings by vm.settings.collectAsState()
        val sections by vm.sections.collectAsState()
        val isDirty by vm.isDirty.collectAsState()
        val scope = rememberCoroutineScope()
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius

        var showClearBgConfirm by remember { mutableStateOf(false) }
        var showPreview by remember { mutableStateOf(false) }
        var previewSession by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) { vm.load() }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.splashQuoteTitle,
                    onBack = {
                        if (isDirty) vm.save()
                        navigator.pop()
                    },
                    action = {
                        Row {
                            IconButton(onClick = {
                                showPreview = true; previewSession++
                            }) {
                                Icon(Icons.Default.Visibility, contentDescription = strings.splashQuotePreview)
                            }
                            if (isDirty) {
                                FilledTonalButton(
                                    onClick = { vm.save() },
                                    modifier = Modifier.padding(end = 0.dp),
                                ) {
                                    Text(strings.actionSave)
                                }
                            }
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

                // ── Master Switch ─────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteEnabled, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(strings.splashQuoteEnabledDesc) },
                        trailingContent = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = { vm.setEnabled(it); if (!it) vm.save() },
                            )
                        },
                    )
                }

                if (!settings.enabled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = radius.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        ),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    strings.splashQuoteSettingsDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    Spacer(Modifier.height(spacing.lg))
                    return@Column
                }

                // ── 1. Splash Quote Section ─────────────────
                ConfigSection(
                    title = strings.splashQuoteTitle,
                    expanded = sections["splash"] ?: true,
                    onToggle = { vm.toggleSection("splash") },
                ) {
                    // Duration
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteDuration) },
                        supportingContent = { Text(strings.splashQuoteDurationDesc) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(
                                    onClick = { vm.setDuration(settings.durationSeconds - 1) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "${settings.durationSeconds}${strings.splashQuoteDurationSeconds}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                FilledIconButton(
                                    onClick = { vm.setDuration(settings.durationSeconds + 1) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        },
                    )

                    // Strategy
                    HorizontalDivider()
                    StrategyItem(
                        strategy = settings.strategy,
                        strings = strings,
                        onChange = { vm.setStrategy(it) },
                    )

                    // Show Author
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteShowAuthor) },
                        supportingContent = { Text(strings.splashQuoteShowAuthorDesc) },
                        trailingContent = {
                            Switch(
                                checked = settings.showAuthor,
                                onCheckedChange = { vm.setShowAuthor(it) },
                            )
                        },
                    )
                }

                // ── 2. Screen Saver Section ────────────────
                ConfigSection(
                    title = strings.settingsScreenSaver,
                    expanded = sections["screen_saver"] ?: true,
                    onToggle = { vm.toggleSection("screen_saver") },
                ) {
                    ListItem(
                        headlineContent = { Text(strings.settingsScreenSaverEnabled) },
                        supportingContent = { Text(strings.settingsScreenSaverDesc) },
                        trailingContent = {
                            Switch(
                                checked = settings.screenSaverEnabled,
                                onCheckedChange = { vm.setScreenSaverEnabled(it) },
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.settingsScreenSaverIdleMinutes) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(
                                    onClick = {
                                        vm.setScreenSaverIdleMinutes(settings.screenSaverIdleMinutes - 1)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "${settings.screenSaverIdleMinutes}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                FilledIconButton(
                                    onClick = {
                                        vm.setScreenSaverIdleMinutes(settings.screenSaverIdleMinutes + 1)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.settingsScreenSaverRotation) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(
                                    onClick = {
                                        vm.setScreenSaverRotationSeconds(settings.screenSaverRotationSeconds - 1)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "${settings.screenSaverRotationSeconds}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                FilledIconButton(
                                    onClick = {
                                        vm.setScreenSaverRotationSeconds(settings.screenSaverRotationSeconds + 1)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        },
                    )
                }

                // ── 3. Quote Management Section ─────────────
                ConfigSection(
                    title = strings.splashQuoteManage,
                    expanded = sections["management"] ?: true,
                    onToggle = { vm.toggleSection("management") },
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteManage) },
                        supportingContent = { Text(strings.splashQuoteManageDesc) },
                        leadingContent = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                        modifier = Modifier.clickable { navigator.push(SplashQuoteListScreen()) },
                        trailingContent = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                // ── 4. Font & Layout Section ────────────────
                ConfigSection(
                    title = strings.splashQuoteFont,
                    expanded = sections["font_layout"] ?: false,
                    onToggle = { vm.toggleSection("font_layout") },
                ) {
                    DirectionItem(
                        direction = settings.direction,
                        strings = strings,
                        onChange = { vm.setDirection(it) },
                    )
                    HorizontalDivider()
                    FontItem(
                        currentFont = settings.font,
                        strings = strings,
                        onChange = { vm.setFont(it) },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteFontSize) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(
                                    onClick = {
                                        val v = (if (settings.fontSize > 0f) settings.fontSize else 24f) - 2f
                                        vm.setFontSize(v.coerceAtLeast(12f))
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    if (settings.fontSize > 0f) "${settings.fontSize.toInt()}" else strings.splashQuoteFontDefault,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                FilledIconButton(
                                    onClick = {
                                        val v = (if (settings.fontSize > 0f) settings.fontSize else 24f) + 2f
                                        vm.setFontSize(v.coerceAtMost(72f))
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        },
                    )
                }

                // ── 5. Animation Section ────────────────────
                ConfigSection(
                    title = strings.splashQuoteAnimation,
                    expanded = sections["animation"] ?: false,
                    onToggle = { vm.toggleSection("animation") },
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteAnimationEnable) },
                        supportingContent = { Text(strings.splashQuoteAnimationDesc) },
                        trailingContent = {
                            Switch(
                                checked = settings.enableAnimation,
                                onCheckedChange = { vm.setEnableAnimation(it) },
                            )
                        },
                    )
                    if (settings.enableAnimation) {
                        HorizontalDivider()
                        AnimationStyleItem(
                            style = settings.animationStyle,
                            strings = strings,
                            onChange = { vm.setAnimationStyle(it) },
                        )
                    }
                }

                // ── 6. Background Section ─────────────────
                ConfigSection(
                    title = strings.splashQuoteBackground,
                    expanded = sections["background"] ?: false,
                    onToggle = { vm.toggleSection("background") },
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteBackgroundDefault) },
                        leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                        modifier = Modifier.clickable { vm.setBackgroundPath("") },
                        trailingContent = {
                            if (settings.backgroundPath.isBlank()) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteBackgroundDesc) },
                        leadingContent = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                val path = withContext(Dispatchers.IO) { pickOpenFile("image/*") }
                                if (path != null) vm.setBackgroundPath(path)
                            }
                        },
                    )
                    if (settings.backgroundPath.isNotBlank()) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.splashQuoteBackgroundClear, color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { showClearBgConfirm = true },
                        )
                    }
                }

                Spacer(Modifier.height(spacing.lg))
            }
        }

        // ── Clear bg confirm ────────────────────────────
        if (showClearBgConfirm) {
            AlertDialog(
                onDismissRequest = { showClearBgConfirm = false },
                title = { Text(strings.splashQuoteClearBgConfirm) },
                confirmButton = {
                    Button(onClick = { vm.setBackgroundPath(""); showClearBgConfirm = false }) {
                        Text(strings.actionConfirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearBgConfirm = false }) { Text(strings.actionCancel) }
                },
            )
        }

        // ── Preview overlay ────────────────────────────
        if (showPreview) {
            val previewQuote = remember { vm.getPreviewQuote() }
            if (previewQuote != null) {
                key(previewSession) {
                    QuoteViewer(
                        quote = previewQuote,
                        config = QuoteDisplayConfig.STARTUP_DEFAULT.copy(
                            defaultDirection = settings.direction,
                            defaultFont = settings.font,
                            defaultFontSize = settings.fontSize,
                            showAuthor = settings.showAuthor,
                            enableAnimation = settings.enableAnimation,
                            animationStyle = settings.animationStyle,
                        ),
                        globalBackgroundPath = settings.backgroundPath,
                        onDismiss = { showPreview = false },
                        onNextQuote = {
                            showPreview = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimationStyleItem(
    style: QuoteAnimationStyle,
    strings: I18nStrings,
    onChange: (QuoteAnimationStyle) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(strings.splashQuoteAnimationStyle) },
        trailingContent = {
            Box {
                TextButton(onClick = { showDropdown = true }) {
                    Text(
                        when (style) {
                            QuoteAnimationStyle.NONE -> strings.splashQuoteAnimationNone
                            QuoteAnimationStyle.TYPEWRITER -> strings.splashQuoteAnimationTypewriter
                            QuoteAnimationStyle.FADE_IN -> strings.splashQuoteAnimationFadeIn
                            QuoteAnimationStyle.SLIDE_UP -> strings.splashQuoteAnimationSlideUp
                            QuoteAnimationStyle.SENTENCE_BY_SENTENCE -> strings.splashQuoteAnimationSentence
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    QuoteAnimationStyle.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(when (s) {
                                    QuoteAnimationStyle.NONE -> strings.splashQuoteAnimationNone
                                    QuoteAnimationStyle.TYPEWRITER -> strings.splashQuoteAnimationTypewriter
                                    QuoteAnimationStyle.FADE_IN -> strings.splashQuoteAnimationFadeIn
                                    QuoteAnimationStyle.SLIDE_UP -> strings.splashQuoteAnimationSlideUp
                                    QuoteAnimationStyle.SENTENCE_BY_SENTENCE -> strings.splashQuoteAnimationSentence
                                })
                            },
                            onClick = { onChange(s); showDropdown = false },
                            leadingIcon = {
                                if (s == style) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun StrategyItem(
    strategy: SplashQuoteStrategy,
    strings: I18nStrings,
    onChange: (SplashQuoteStrategy) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(strings.splashQuoteStrategy) },
        trailingContent = {
            Box {
                TextButton(onClick = { showDropdown = true }) {
                    Text(
                        when (strategy) {
                            SplashQuoteStrategy.RANDOM -> strings.splashQuoteStrategyRandom
                            SplashQuoteStrategy.SEQUENTIAL -> strings.splashQuoteStrategySequential
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    SplashQuoteStrategy.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(when (s) {
                                    SplashQuoteStrategy.RANDOM -> strings.splashQuoteStrategyRandom
                                    SplashQuoteStrategy.SEQUENTIAL -> strings.splashQuoteStrategySequential
                                })
                            },
                            onClick = { onChange(s); showDropdown = false },
                            leadingIcon = {
                                if (s == strategy) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DirectionItem(
    direction: SplashQuoteDirection,
    strings: I18nStrings,
    onChange: (SplashQuoteDirection) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(strings.splashQuoteDirection) },
        trailingContent = {
            Box {
                TextButton(onClick = { showDropdown = true }) {
                    Text(
                        when (direction) {
                            SplashQuoteDirection.HORIZONTAL -> strings.splashQuoteDirectionHorizontal
                            SplashQuoteDirection.VERTICAL -> strings.splashQuoteDirectionVertical
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    SplashQuoteDirection.entries.forEach { dir ->
                        DropdownMenuItem(
                            text = {
                                Text(when (dir) {
                                    SplashQuoteDirection.HORIZONTAL -> strings.splashQuoteDirectionHorizontal
                                    SplashQuoteDirection.VERTICAL -> strings.splashQuoteDirectionVertical
                                })
                            },
                            onClick = { onChange(dir); showDropdown = false },
                            leadingIcon = {
                                if (dir == direction) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun FontItem(
    currentFont: String,
    strings: I18nStrings,
    onChange: (String) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val allFontSpecs = FontRegistry.fonts
    val currentFontName = FontRegistry.findById(currentFont)?.displayName ?: strings.splashQuoteFontDefault
    ListItem(
        headlineContent = { Text(strings.splashQuoteFont) },
        trailingContent = {
            Box {
                TextButton(onClick = { showDropdown = true }) {
                    Text(currentFontName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.splashQuoteFontDefault) },
                        onClick = { onChange(""); showDropdown = false },
                    )
                    allFontSpecs.forEach { spec ->
                        DropdownMenuItem(
                            text = { Text(spec.displayName) },
                            onClick = { onChange(spec.id); showDropdown = false },
                            leadingIcon = {
                                if (spec.id == currentFont) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }
        },
    )
}
