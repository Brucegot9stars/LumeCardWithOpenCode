package com.lumecard.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import com.lumecard.app.data.AiCardGenerationManager
import com.lumecard.app.feature.quote.screen.ScreenSaverOverlay
import com.lumecard.app.feature.quote.viewer.QuoteViewer
import com.lumecard.app.i18n.AppLocale
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.screens.aicard.AiCardScreen
import com.lumecard.app.ui.screens.dashboard.DashboardScreen
import com.lumecard.app.ui.screens.settings.SettingsScreen
import com.lumecard.app.ui.screens.settings.SettingsStateHolder
import com.lumecard.app.ui.screens.stats.StatsScreen
import com.lumecard.app.ui.screens.warehouse.WarehouseScreen
import com.lumecard.app.font.FontInitializer
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.feature.quote.config.QuoteDisplayMode
import com.lumecard.shared.feature.quote.facade.QuoteFeature
import com.lumecard.shared.repository.SettingsRepository
import org.koin.compose.koinInject

enum class BottomNavItem(val icon: ImageVector) {
    Dashboard(Icons.Default.Home),
    Stats(Icons.Default.DateRange),
    Warehouse(Icons.AutoMirrored.Filled.List),
    Settings(Icons.Default.Settings)
}

var savedCrashLog: String? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val settingsRepository: SettingsRepository = koinInject()
    FontInitializer.ensureInitialized(settingsRepository)
    val settingsStateHolder: SettingsStateHolder = koinInject()
    val i18nManager: I18nManager = koinInject()
    val strings = i18nManager.strings
    val quoteFeature: QuoteFeature = koinInject()

    var crashLog by remember {
        val fromHolder = CrashLogHolder.lastCrashLog
        CrashLogHolder.lastCrashLog = null
        mutableStateOf(fromHolder)
    }

    // ── Startup splash state ─────────────────────────────
    var showSplash by remember { mutableStateOf(false) }
    var startupQuote by remember { mutableStateOf<com.lumecard.shared.data.SplashQuoteData?>(null) }
    var startupBackgroundPath by remember { mutableStateOf("") }
    var startupDurationMs by remember { mutableStateOf(3000L) }
    val activityScope = rememberCoroutineScope()

    // ── Screen saver state ───────────────────────────────
    var showScreenSaver by remember { mutableStateOf(false) }
    var screenSaverEnabled by remember { mutableStateOf(true) }
    var screenSaverRotationMs by remember { mutableStateOf(3000L) }
    var screenSaverGlobalBg by remember { mutableStateOf("") }

    // ── Initial load ─────────────────────────────────────
    LaunchedEffect(Unit) {
        settingsStateHolder.isDarkMode = settingsRepository.getBoolean("isDarkMode", false)
        settingsStateHolder.fontScale = settingsRepository.get("fontScale")?.toFloatOrNull() ?: 1.0f
        val langStr = settingsRepository.get("language") ?: AppLocale.SYSTEM.name
        val savedLang = try { AppLocale.valueOf(langStr) } catch (_: Exception) { AppLocale.SYSTEM }
        settingsStateHolder.language = savedLang
        i18nManager.setLocale(savedLang)

        quoteFeature.load()
        val settings = quoteFeature.loadSettings()
        if (settings.enabled) {
            quoteFeature.startDisplay(QuoteDisplayMode.STARTUP, settings)
            startupQuote = quoteFeature.currentQuote.value
            startupBackgroundPath = settings.backgroundPath
            startupDurationMs = settings.durationSeconds * 1000L
            showSplash = startupQuote != null
        }

        // Init screen saver settings
        val ss = quoteFeature.loadScreenSaverSettings()
        screenSaverEnabled = ss.enabled
        screenSaverRotationMs = ss.rotationSeconds * 1000L
        screenSaverGlobalBg = settings.backgroundPath
        if (ss.enabled) {
            quoteFeature.setIdleThreshold(ss.idleMinutes * 60_000L)
        }

        // Init idle pause settings (for study timer)
        val ips = quoteFeature.loadIdlePauseSettings()
        quoteFeature.setIdlePauseEnabled(ips.enabled)
        if (ips.enabled) {
            quoteFeature.setIdlePauseThreshold(ips.thresholdSeconds * 1000L)
        }
    }

    // ── Screen saver idle detection ──────────────────────
    LaunchedEffect(screenSaverEnabled) {
        if (screenSaverEnabled) {
            snapshotFlow { quoteFeature.isIdle.value }
                .collect { idle ->
                    if (idle && !showScreenSaver) {
                        quoteFeature.startDisplay(
                            QuoteDisplayMode.SCREEN_SAVER,
                            quoteFeature.loadSettings(),
                        )
                        showScreenSaver = true
                    }
                }
        }
    }

    // ── User activity handler ────────────────────────────
    fun reportActivity() {
        quoteFeature.reportActivity()
        if (showScreenSaver) {
            showScreenSaver = false
            activityScope.launch { quoteFeature.stopDisplay() }
        }
    }

    // ── Crash dialog ─────────────────────────────────────
    if (crashLog != null) {
        @Suppress("DEPRECATION")
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = {
                crashLog = null
                savedCrashLog = null
            },
            title = { Text(strings.crashAppError) },
            text = {
                Column {
                    Text(strings.crashAppErrorDesc, style = MaterialTheme.typography.bodyMedium)
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
                            text = crashLog ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        crashLog?.let { clipboardManager.setText(AnnotatedString(it)) }
                    }) {
                        Text(strings.actionCopy)
                    }
                    Button(onClick = {
                        crashLog = null
                        savedCrashLog = null
                    }) {
                        Text(strings.actionOk)
                    }
                }
            },
        )
    }

    LumeCardTheme(darkTheme = settingsStateHolder.isDarkMode, fontScale = settingsStateHolder.fontScale) {
        // ── Wrap all content to capture user activity ─────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            reportActivity()
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        reportActivity()
                        true
                    } else false
                },
        ) {
            if (showSplash && startupQuote != null) {
                // Startup quote using unified QuoteViewer
                QuoteViewer(
                    quote = startupQuote!!,
                    config = com.lumecard.shared.feature.quote.config.QuoteDisplayConfig.STARTUP_DEFAULT.copy(
                        dismissDurationMs = startupDurationMs,
                    ),
                    globalBackgroundPath = startupBackgroundPath,
                    onDismiss = { showSplash = false },
                )
            } else {
                // Screen saver overlay (on top of main content)
                if (showScreenSaver && screenSaverEnabled) {
                    ScreenSaverOverlay(
                        currentQuote = quoteFeature.currentQuote.value,
                        config = com.lumecard.shared.feature.quote.config.QuoteDisplayConfig.SCREEN_SAVER_DEFAULT.copy(
                            rotationIntervalMs = screenSaverRotationMs,
                        ),
                        globalBackgroundPath = screenSaverGlobalBg,
                        onUserActivity = { reportActivity() },
                        onNextQuote = { quoteFeature.nextQuote() },
                    )
                }

                // Main app content
                var currentTab by remember { mutableStateOf(BottomNavItem.Dashboard) }

                Navigator(DashboardScreen()) { navigator ->
                    LaunchedEffect(currentTab) {
                        val screen = when (currentTab) {
                            BottomNavItem.Dashboard -> DashboardScreen()
                            BottomNavItem.Stats -> StatsScreen()
                            BottomNavItem.Warehouse -> WarehouseScreen()
                            BottomNavItem.Settings -> SettingsScreen(onNavigateToHome = { currentTab = BottomNavItem.Dashboard })
                        }
                        val currentScreen = navigator.lastItemOrNull
                        if (currentScreen?.key != screen.key) {
                            withFrameNanos { navigator.replaceAll(screen) }
                        }
                    }

                    val manager = koinInject<AiCardGenerationManager>()
                    val aiState by manager.state.collectAsState()
                    val batchProgress = aiState.batchProgress

                    Box(Modifier.fillMaxSize()) {
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    BottomNavItem.entries.forEach { item ->
                                        NavigationBarItem(
                                            selected = currentTab == item,
                                            onClick = { currentTab = item },
                                            icon = { Icon(item.icon, contentDescription = null) },
                                            label = {
                                                val label = when (item) {
                                                    BottomNavItem.Dashboard -> strings.navHome
                                                    BottomNavItem.Stats -> strings.navStats
                                                    BottomNavItem.Warehouse -> strings.warehouseTitle
                                                    BottomNavItem.Settings -> strings.navSettings
                                                }
                                                Text(label)
                                            }
                                        )
                                    }
                                }
                            }
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                cafe.adriel.voyager.navigator.CurrentScreen()
                            }
                        }

                        val infiniteTransition = rememberInfiniteTransition()
                        val breatheAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        )

                        AnimatedVisibility(
                            visible = batchProgress != null,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .clickable { navigator.push(AiCardScreen()) }
                                    .graphicsLayer { alpha = breatheAlpha },
                                shape = RoundedCornerShape(24.dp),
                                shadowElevation = 8.dp,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Autorenew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    val bp = batchProgress
                                    if (bp != null) {
                                        Text(
                                            text = "Batch ${bp.currentBatch}/${bp.totalBatches} · ${bp.savedCards}/${bp.totalTarget}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
