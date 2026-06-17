package com.lumecard.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.navigator.Navigator
import com.lumecard.app.i18n.AppLocale
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.screens.dashboard.DashboardScreen
import com.lumecard.app.ui.screens.settings.SettingsScreen
import com.lumecard.app.ui.screens.settings.SettingsStateHolder
import com.lumecard.app.ui.screens.stats.StatsScreen
import com.lumecard.app.ui.screens.warehouse.WarehouseScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.repository.SettingsRepository
import org.koin.compose.koinInject

enum class BottomNavItem(val icon: ImageVector) {
    Dashboard(Icons.Default.Home),
    Stats(Icons.Default.DateRange),
    Warehouse(Icons.Default.List),
    Settings(Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val settingsStateHolder: SettingsStateHolder = koinInject()
    val i18nManager: I18nManager = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val strings = i18nManager.strings

    LaunchedEffect(Unit) {
        settingsStateHolder.isDarkMode = settingsRepository.getBoolean("isDarkMode", false)
        val langStr = settingsRepository.get("language") ?: AppLocale.SYSTEM.name
        val savedLang = try { AppLocale.valueOf(langStr) } catch (_: Exception) { AppLocale.SYSTEM }
        settingsStateHolder.language = savedLang
        i18nManager.setLocale(savedLang)
    }

    LumeCardTheme(darkTheme = settingsStateHolder.isDarkMode) {
            var currentTab by remember { mutableStateOf(BottomNavItem.Dashboard) }

            Navigator(DashboardScreen()) { navigator ->
                LaunchedEffect(currentTab) {
                    val screen = when (currentTab) {
                        BottomNavItem.Dashboard -> DashboardScreen()
                        BottomNavItem.Stats -> StatsScreen()
                        BottomNavItem.Warehouse -> WarehouseScreen()
                        BottomNavItem.Settings -> SettingsScreen()
                    }
                    navigator.replace(screen)
                }

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
            }
    }
}
