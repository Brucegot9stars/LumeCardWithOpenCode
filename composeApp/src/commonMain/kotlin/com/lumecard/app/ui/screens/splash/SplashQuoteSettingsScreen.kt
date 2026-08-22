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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.SplashQuoteDirection
import com.lumecard.shared.data.SplashQuoteStrategy
import com.lumecard.shared.data.SplashQuoteData
import com.lumecard.app.platform.pickOpenFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class SplashQuoteSettingsScreen : Screen {
    override val key: ScreenKey = "SplashQuoteSettings"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val strings = koinInject<I18nManager>().strings
        val vm: SplashQuoteViewModel = koinInject()
        val settings by vm.settings.collectAsState()
        val scope = rememberCoroutineScope()
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius

        var showClearBgConfirm by remember { mutableStateOf(false) }
        var showPreview by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { vm.load() }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.splashQuoteTitle,
                    onBack = { navigator.pop() },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.section),
            ) {
                Spacer(Modifier.height(spacing.xs))

                // ── 1. Master Switch ─────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteEnabled) },
                        supportingContent = { Text(strings.splashQuoteEnabledDesc) },
                        trailingContent = {
                            Switch(checked = settings.enabled, onCheckedChange = { vm.setEnabled(it); vm.save() })
                        },
                    )
                }

                if (!settings.enabled) return@Column

                // ── 2. Duration ──────────────────────────
                SectionHeader(strings.splashQuoteDuration)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(onClick = { vm.setDuration(settings.durationSeconds - 1) }, modifier = Modifier.size(32.dp)) {
                                Text("-", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "${settings.durationSeconds}${strings.splashQuoteDurationSeconds}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            FilledIconButton(onClick = { vm.setDuration(settings.durationSeconds + 1) }, modifier = Modifier.size(32.dp)) {
                                Text("+", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                // ── 3. Direction ─────────────────────────
                SectionHeader(strings.splashQuoteDirection)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    var showDirDropdown by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteDirection) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showDirDropdown = true }) {
                                    Text(
                                        when (settings.direction) {
                                            SplashQuoteDirection.HORIZONTAL -> strings.splashQuoteDirectionHorizontal
                                            SplashQuoteDirection.VERTICAL -> strings.splashQuoteDirectionVertical
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = showDirDropdown, onDismissRequest = { showDirDropdown = false }) {
                                    SplashQuoteDirection.entries.forEach { dir ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(when (dir) {
                                                    SplashQuoteDirection.HORIZONTAL -> strings.splashQuoteDirectionHorizontal
                                                    SplashQuoteDirection.VERTICAL -> strings.splashQuoteDirectionVertical
                                                })
                                            },
                                            onClick = { vm.setDirection(dir); vm.save(); showDirDropdown = false },
                                            leadingIcon = {
                                                if (dir == settings.direction) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                // ── 4. Font ──────────────────────────────
                SectionHeader(strings.splashQuoteFont)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column {
                        var showFontDropdown by remember { mutableStateOf(false) }
                        val allFontSpecs = FontRegistry.fonts
                        val currentFontName = FontRegistry.findById(settings.font)?.displayName ?: "Default"
                        ListItem(
                            headlineContent = { Text(strings.splashQuoteFont) },
                            trailingContent = {
                                Box {
                                    TextButton(onClick = { showFontDropdown = true }) {
                                        Text(currentFontName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = showFontDropdown, onDismissRequest = { showFontDropdown = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Default") },
                                            onClick = { vm.setFont(""); vm.save(); showFontDropdown = false },
                                        )
                                        allFontSpecs.forEach { spec ->
                                            DropdownMenuItem(
                                                text = { Text(spec.displayName) },
                                                onClick = { vm.setFont(spec.id); vm.save(); showFontDropdown = false },
                                                leadingIcon = {
                                                    if (spec.id == settings.font) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.splashQuoteFontSize) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledIconButton(onClick = {
                                        val v = (if (settings.fontSize > 0f) settings.fontSize else 24f) - 2f
                                        vm.setFontSize(v.coerceAtLeast(12f)); vm.save()
                                    }, modifier = Modifier.size(32.dp)) {
                                        Text("-", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        if (settings.fontSize > 0f) "${settings.fontSize.toInt()}" else "Default",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                    FilledIconButton(onClick = {
                                        val v = (if (settings.fontSize > 0f) settings.fontSize else 24f) + 2f
                                        vm.setFontSize(v.coerceAtMost(72f)); vm.save()
                                    }, modifier = Modifier.size(32.dp)) {
                                        Text("+", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            },
                        )
                    }
                }

                // ── 5. Background ────────────────────────
                SectionHeader(strings.splashQuoteBackground)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(strings.splashQuoteBackgroundDefault) },
                            leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                            modifier = Modifier.clickable {
                                vm.setBackgroundPath(""); vm.save()
                            },
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
                                    if (path != null) {
                                        vm.setBackgroundPath(path); vm.save()
                                    }
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

                // ── 6. Strategy ──────────────────────────
                SectionHeader(strings.splashQuoteStrategy)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    var showStrategyDropdown by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteStrategy) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { showStrategyDropdown = true }) {
                                    Text(
                                        when (settings.strategy) {
                                            SplashQuoteStrategy.RANDOM -> strings.splashQuoteStrategyRandom
                                            SplashQuoteStrategy.SEQUENTIAL -> strings.splashQuoteStrategySequential
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = showStrategyDropdown, onDismissRequest = { showStrategyDropdown = false }) {
                                    SplashQuoteStrategy.entries.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(when (s) { SplashQuoteStrategy.RANDOM -> strings.splashQuoteStrategyRandom; SplashQuoteStrategy.SEQUENTIAL -> strings.splashQuoteStrategySequential }) },
                                            onClick = { vm.setStrategy(s); vm.save(); showStrategyDropdown = false },
                                            leadingIcon = {
                                                if (s == settings.strategy) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                // ── 7. Show Author ───────────────────────
                SectionHeader(strings.splashQuoteShowAuthor)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteShowAuthor) },
                        supportingContent = { Text(strings.splashQuoteShowAuthorDesc) },
                        trailingContent = {
                            Switch(checked = settings.showAuthor, onCheckedChange = { vm.setShowAuthor(it); vm.save() })
                        },
                    )
                }

                // ── 8. Preview ───────────────────────────
                SectionHeader(strings.splashQuotePreview)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuotePreview) },
                        supportingContent = { Text(strings.splashQuotePreviewDesc) },
                        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        modifier = Modifier.clickable { showPreview = true },
                    )
                }

                // ── 9. Quote Management ──────────────────
                SectionHeader(strings.splashQuoteManage)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = radius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    ListItem(
                        headlineContent = { Text(strings.splashQuoteManage) },
                        supportingContent = { Text(strings.splashQuoteManageDesc) },
                        leadingContent = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                        modifier = Modifier.clickable { navigator.push(SplashQuoteListScreen()) },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )
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
                    Button(onClick = { vm.setBackgroundPath(""); vm.save(); showClearBgConfirm = false }) {
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
            val quote by produceState<SplashQuoteData?>(null) {
                value = vm.getQuoteForPreview()
            }
            if (quote != null) {
                val q = quote!!
                SplashQuoteScreen(
                    quote = q,
                    direction = settings.direction,
                    splashFontId = settings.font,
                    splashFontSize = settings.fontSize,
                    durationMs = settings.durationSeconds * 1000L,
                    backgroundPath = settings.backgroundPath,
                    showAuthor = settings.showAuthor,
                    overrideConfig = q.overrideConfig,
                    onDismiss = { showPreview = false },
                )
            } else {
                LaunchedEffect(Unit) { showPreview = false }
            }
        }
    }
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
