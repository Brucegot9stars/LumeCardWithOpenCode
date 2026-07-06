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
import com.lumecard.app.ui.components.SettingsSearchBar
import com.lumecard.app.ui.screens.splash.SplashQuoteListScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.SplashQuoteDirection
import com.lumecard.shared.data.SplashQuoteStrategy
import com.lumecard.app.platform.pickOpenFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private data class SearchableItem(
    val label: String,
    val description: String = "",
)

private data class SearchableSection(
    val id: String,
    val title: String,
    val items: List<SearchableItem>,
)

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
        val searchQuery by vm.searchQuery.collectAsState()
        val isDirty by vm.isDirty.collectAsState()
        val scope = rememberCoroutineScope()
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius

        var showClearBgConfirm by remember { mutableStateOf(false) }
        var showPreview by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { vm.load() }

        val isSearching = searchQuery.isNotBlank()

        // Define sections with searchable content
        val sectionDefs = remember(strings) {
            listOf(
                SearchableSection("splash", strings.splashQuoteTitle, listOf(
                    SearchableItem(strings.splashQuoteDuration, strings.splashQuoteDurationDesc),
                    SearchableItem(strings.splashQuoteStrategy, ""),
                    SearchableItem(strings.splashQuoteShowAuthor, strings.splashQuoteShowAuthorDesc),
                )),
                SearchableSection("screen_saver", strings.settingsScreenSaver, listOf(
                    SearchableItem(strings.settingsScreenSaverEnabled, strings.settingsScreenSaverDesc),
                    SearchableItem(strings.settingsScreenSaverIdleMinutes, ""),
                    SearchableItem(strings.settingsScreenSaverRotation, ""),
                )),
                SearchableSection("management", strings.splashQuoteManage, listOf(
                    SearchableItem(strings.splashQuoteManage, strings.splashQuoteManageDesc),
                )),
                SearchableSection("font_layout", strings.splashQuoteFont, listOf(
                    SearchableItem(strings.splashQuoteDirection, ""),
                    SearchableItem(strings.splashQuoteFont, ""),
                    SearchableItem(strings.splashQuoteFontSize, ""),
                )),
                SearchableSection("animation", strings.splashQuoteAnimation, emptyList()),
                SearchableSection("background", strings.splashQuoteBackground, listOf(
                    SearchableItem(strings.splashQuoteBackgroundDefault, ""),
                    SearchableItem(strings.splashQuoteBackgroundDesc, ""),
                )),
            )
        }

        // Compute search matches
        val matchedSections = remember(sectionDefs, searchQuery) {
            if (searchQuery.isBlank()) {
                sectionDefs.associate { it.id to it.items.indices.toList() }
            } else {
                val q = searchQuery.lowercase()
                sectionDefs.associate { section ->
                    val titleMatch = section.title.lowercase().contains(q)
                    val matchedIndices = section.items.mapIndexedNotNull { i, item ->
                        if (titleMatch || item.label.lowercase().contains(q) || item.description.lowercase().contains(q)) i else null
                    }
                    section.id to matchedIndices
                }
            }
        }

        // Auto-expand matching sections during search
        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotBlank()) {
                matchedSections.forEach { (id, indices) ->
                    if (indices.isNotEmpty() && !(sections[id] ?: false)) {
                        vm.setSection(id, true)
                    }
                }
            }
        }

        fun matchesSearch(sectionId: String): Boolean {
            val indices = matchedSections[sectionId] ?: emptyList()
            return !isSearching || indices.isNotEmpty()
        }

        fun itemVisible(sectionId: String, itemIndex: Int): Boolean {
            if (!isSearching) return true
            return matchedSections[sectionId]?.contains(itemIndex) ?: false
        }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.splashQuoteTitle,
                    onBack = {
                        if (isDirty) vm.save()
                        navigator.pop()
                    },
                    action = {
                        if (isDirty) {
                            FilledTonalButton(
                                onClick = { vm.save() },
                                modifier = Modifier.padding(end = 0.dp),
                            ) {
                                Text(strings.actionSave)
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

                // ── Search Bar ────────────────────────────────
                SettingsSearchBar(
                    query = searchQuery,
                    onQueryChange = { vm.setSearchQuery(it) },
                    placeholder = strings.actionSearch,
                )

                // ── 1. Splash Quote Section ─────────────────
                if (matchesSearch("splash")) {
                    ConfigSection(
                        title = strings.splashQuoteTitle,
                        expanded = sections["splash"] ?: true,
                        onToggle = { vm.toggleSection("splash") },
                    ) {
                        // Duration
                        if (itemVisible("splash", 0)) {
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
                        }

                        // Strategy
                        if (itemVisible("splash", 1)) {
                            HorizontalDivider()
                            StrategyItem(
                                strategy = settings.strategy,
                                strings = strings,
                                onChange = { vm.setStrategy(it) },
                            )
                        }

                        // Show Author
                        if (itemVisible("splash", 2)) {
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
                    }
                }

                // ── 2. Screen Saver Section ────────────────
                if (matchesSearch("screen_saver")) {
                    ConfigSection(
                        title = strings.settingsScreenSaver,
                        expanded = sections["screen_saver"] ?: true,
                        onToggle = { vm.toggleSection("screen_saver") },
                    ) {
                        if (itemVisible("screen_saver", 0)) {
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
                        }
                        if (itemVisible("screen_saver", 1)) {
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
                        }
                        if (itemVisible("screen_saver", 2)) {
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
                    }
                }

                // ── 3. Quote Management Section ─────────────
                if (matchesSearch("management")) {
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
                }

                // ── 4. Font & Layout Section ────────────────
                if (matchesSearch("font_layout")) {
                    ConfigSection(
                        title = strings.splashQuoteFont,
                        expanded = sections["font_layout"] ?: false,
                        onToggle = { vm.toggleSection("font_layout") },
                    ) {
                        if (itemVisible("font_layout", 0)) {
                            DirectionItem(
                                direction = settings.direction,
                                strings = strings,
                                onChange = { vm.setDirection(it) },
                            )
                        }
                        if (itemVisible("font_layout", 1)) {
                            HorizontalDivider()
                            FontItem(
                                currentFont = settings.font,
                                strings = strings,
                                onChange = { vm.setFont(it) },
                            )
                        }
                        if (itemVisible("font_layout", 2)) {
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
                    }
                }

                // ── 5. Animation Section (placeholder) ──────
                if (matchesSearch("animation")) {
                    ConfigSection(
                        title = strings.splashQuoteAnimation,
                        expanded = sections["animation"] ?: false,
                        onToggle = { vm.toggleSection("animation") },
                    ) {
                        ListItem(
                            headlineContent = { Text(strings.splashQuoteAnimation) },
                            supportingContent = { Text(strings.splashQuoteAnimationDesc) },
                        )
                    }
                }

                // ── 6. Background Section ─────────────────
                if (matchesSearch("background")) {
                    ConfigSection(
                        title = strings.splashQuoteBackground,
                        expanded = sections["background"] ?: false,
                        onToggle = { vm.toggleSection("background") },
                    ) {
                        if (itemVisible("background", 0)) {
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
                        }
                        if (itemVisible("background", 1)) {
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
            // Preview functionality kept from original; can be enhanced later
        }
    }
}

// ── Helper composables ───────────────────────────────

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
