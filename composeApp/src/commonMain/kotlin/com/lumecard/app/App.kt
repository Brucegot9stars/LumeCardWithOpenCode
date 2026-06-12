package com.lumecard.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.lumecard.app.di.appModule
import com.lumecard.app.ui.navigation.LumeCardScreen
import com.lumecard.app.ui.screens.dashboard.DashboardScreen
import com.lumecard.app.ui.screens.settings.SettingsScreen
import com.lumecard.app.ui.screens.stats.StatsScreen
import com.lumecard.app.ui.theme.LumeCardTheme
import org.koin.compose.KoinApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    KoinApplication(
        application = {
            modules(appModule)
        }
    ) {
        LumeCardTheme {
            TabNavigator(DashboardTab) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TabNavigationItem(DashboardTab)
                            TabNavigationItem(StatsTab)
                            TabNavigationItem(SettingsTab)
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        CurrentTab()
                    }
                }
            }
        }
    }
}

// Tab definitions
object DashboardTab : Tab {
    override val options: Tab.TabOptions
        @Composable
        get() = Tab.TabOptions(
            index = 0u,
            title = "首页",
            icon = Icons.Default.Home
        )

    @Composable
    override fun Content() {
        Navigator(screen = LumeCardScreen.Dashboard)
    }
}

object StatsTab : Tab {
    override val options: Tab.TabOptions
        @Composable
        get() = Tab.TabOptions(
            index = 1u,
            title = "统计",
            icon = Icons.Default.BarChart
        )

    @Composable
    override fun Content() {
        Navigator(screen = LumeCardScreen.Stats)
    }
}

object SettingsTab : Tab {
    override val options: Tab.TabOptions
        @Composable
        get() = Tab.TabOptions(
            index = 2u,
            title = "设置",
            icon = Icons.Default.Settings
        )

    @Composable
    override fun Content() {
        Navigator(screen = LumeCardScreen.Settings)
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    NavigationBarItem(
        selected = tabNavigator.current.key == tab.key,
        onClick = { tabNavigator.current = tab },
        icon = {
            Icon(
                imageVector = tab.options.icon!!,
                contentDescription = tab.options.title
            )
        },
        label = { Text(tab.options.title) }
    )
}
