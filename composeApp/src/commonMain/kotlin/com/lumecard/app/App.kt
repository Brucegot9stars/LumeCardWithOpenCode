package com.lumecard.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.navigator.Navigator
import com.lumecard.app.di.appModule
import com.lumecard.app.ui.screens.dashboard.DashboardScreen
import com.lumecard.app.ui.screens.settings.SettingsScreen
import com.lumecard.app.ui.screens.settings.ThemeStateHolder
import com.lumecard.app.ui.screens.stats.StatsScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

enum class BottomNavItem(val label: String, val icon: ImageVector) {
    Dashboard("首页", Icons.Default.Home),
    Stats("统计", Icons.Default.DateRange),
    Settings("设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    KoinApplication(
        application = {
            modules(appModule)
        }
    ) {
        val themeStateHolder: ThemeStateHolder = koinInject()
        LumeCardTheme(darkTheme = themeStateHolder.isDarkMode) {
            var currentTab by remember { mutableStateOf(BottomNavItem.Dashboard) }

            Navigator(DashboardScreen()) { navigator ->
                LaunchedEffect(currentTab) {
                    val screen = when (currentTab) {
                        BottomNavItem.Dashboard -> DashboardScreen()
                        BottomNavItem.Stats -> StatsScreen()
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
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) }
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
}
