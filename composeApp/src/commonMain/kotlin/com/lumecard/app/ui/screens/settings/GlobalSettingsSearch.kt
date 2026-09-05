package com.lumecard.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.settings.*

@Composable
fun rememberSettingsSearchEngine(strings: I18nStrings): SettingsSearchEngine {
    val engine = remember { SettingsSearchEngine() }
    val entries = remember(strings) { buildGlobalSettingsIndex(strings) }
    LaunchedEffect(entries) {
        engine.rebuild(entries)
    }
    return engine
}

fun buildGlobalSettingsIndex(strings: I18nStrings): List<SettingsIndexEntry> = buildList {
    add(entry("daily_goal", strings.settingsDailyGoal, strings.settingsDailyGoalDesc, strings.settingsLearning, strings.settingsLearning, "Settings", 10,
        listOf("goal", "daily", "cards", "复习量", "每日")))
    add(entry("new_cards_per_day", strings.settingsNewCards, strings.settingsNewCardsDesc, strings.settingsLearning, strings.settingsLearning, "Settings", 20,
        listOf("new", "cards", "per day", "新卡片")))
    add(entry("review_mode", strings.settingsReviewMode, "", strings.settingsLearning, strings.settingsLearning, "Settings", 30,
        listOf("review", "mode", "算法", "复习")))
    add(entry("answer_display", strings.settingsAnswerMode, "", strings.settingsAnswerDisplay, strings.settingsAnswerDisplay, "Settings", 40,
        listOf("answer", "display", "显示", "答案")))

    add(entry("splash_quote", strings.splashQuoteTitle, strings.splashQuoteSettingsDesc, strings.splashQuoteTitle, strings.splashQuoteTitle, "QuoteSettings", 100,
        listOf("splash", "quote", "名言", "启动")))
    add(entry("splash_quote_duration", strings.splashQuoteDuration, strings.splashQuoteDurationDesc, strings.splashQuoteTitle, strings.splashQuoteTitle, "QuoteSettings", 110,
        listOf("duration", "时长", "显示")))
    add(entry("splash_quote_strategy", strings.splashQuoteStrategy, "", strings.splashQuoteTitle, strings.splashQuoteTitle, "QuoteSettings", 120,
        listOf("strategy", "策略", "随机", "顺序")))
    add(entry("splash_quote_show_author", strings.splashQuoteShowAuthor, strings.splashQuoteShowAuthorDesc, strings.splashQuoteTitle, strings.splashQuoteTitle, "QuoteSettings", 130,
        listOf("author", "作者", "显示")))
    add(entry("screen_saver", strings.settingsScreenSaver, strings.settingsScreenSaverDesc, strings.settingsScreenSaver, strings.settingsScreenSaver, "QuoteSettings", 200,
        listOf("screen", "saver", "屏保", "保护")))
    add(entry("screen_saver_idle", strings.settingsScreenSaverIdleMinutes, "", strings.settingsScreenSaver, strings.settingsScreenSaver, "QuoteSettings", 210,
        listOf("idle", "timeout", "超时", "闲置")))
    add(entry("screen_saver_rotation", strings.settingsScreenSaverRotation, "", strings.settingsScreenSaver, strings.settingsScreenSaver, "QuoteSettings", 220,
        listOf("rotation", "轮播", "间隔")))
    add(entry("quote_direction", strings.splashQuoteDirection, "", strings.splashQuoteFont, strings.splashQuoteFont, "QuoteSettings", 300,
        listOf("direction", "方向", "横排", "竖排")))
    add(entry("quote_font", strings.splashQuoteFont, "", strings.splashQuoteFont, strings.splashQuoteFont, "QuoteSettings", 310,
        listOf("font", "字体")))
    add(entry("quote_font_size", strings.splashQuoteFontSize, "", strings.splashQuoteFont, strings.splashQuoteFont, "QuoteSettings", 320,
        listOf("font size", "字号")))
    add(entry("quote_background", strings.splashQuoteBackground, "", strings.splashQuoteBackground, strings.splashQuoteBackground, "QuoteSettings", 500,
        listOf("background", "背景", "image")))
    add(entry("quote_management", strings.splashQuoteManage, strings.splashQuoteManageDesc, strings.splashQuoteManage, strings.splashQuoteManage, "QuoteSettings", 600,
        listOf("manage", "管理", "list")))

    add(entry("dark_mode", strings.settingsDarkMode, "", strings.settingsAppearance, strings.settingsAppearance, "Settings", 700,
        listOf("dark", "theme", "夜间", "深色")))
    add(entry("language", strings.settingsLanguage, "", strings.settingsAppearance, strings.settingsAppearance, "Settings", 710,
        listOf("language", "语言", "locale")))
    add(entry("default_font", strings.settingsFontTitle, "", strings.settingsAppearance, strings.settingsAppearance, "Settings", 720,
        listOf("font", "字体")))
    add(entry("font_scale", strings.settingsFontScale, "", strings.settingsAppearance, strings.settingsAppearance, "Settings", 730,
        listOf("font", "scale", "缩放")))

    add(entry("notifications", strings.settingsNotifications, "", strings.settingsNotifications, strings.settingsNotifications, "Settings", 800,
        listOf("notification", "通知")))

    add(entry("auto_sync", strings.settingsAutoSync, "", strings.settingsDataManagement, strings.settingsDataManagement, "Settings", 900,
        listOf("sync", "同步")))
    add(entry("webdav_config", strings.settingsCloudSync, strings.settingsCloudSyncDesc, strings.settingsDataManagement, strings.settingsDataManagement, "WebDAV", 910,
        listOf("webdav", "sync", "cloud", "同步", "云")))

    add(entry("ai_config", strings.aiConfig, strings.aiConfigDesc, strings.aiConfig, strings.aiConfig, "AIConfig", 1000,
        listOf("ai", "config", "配置", "人工智能")))

    add(entry("help_center", strings.helpCenter, strings.helpCenterSearchPlaceholder, strings.helpCenter, strings.helpCenter, "HelpCenter", 1050,
        listOf("help", "guide", "FAQ", "帮助", "ガイド", "guía")))
    add(entry("study_timer_idle", strings.settingsStudyTimerIdlePause, strings.settingsStudyTimerIdlePauseDesc, strings.settingsStudyTimer, strings.settingsStudyTimer, "Settings", 1100,
        listOf("study", "timer", "计时", "idle", "暂停")))

    add(entry("data_management", strings.settingsDataManagement, "", strings.settingsDataManagement, strings.settingsDataManagement, "Settings", 1200,
        listOf("data", "manage", "export", "import", "导出", "导入")))

    add(entry("about", strings.settingsAbout, "", strings.settingsAbout, strings.settingsAbout, "Settings", 1300,
        listOf("about", "关于", "version")))
    add(entry("update_check", strings.updateChecking, strings.updateCheckingDesc, strings.settingsAbout, strings.settingsAbout, "Settings", 1310,
        listOf("update", "更新")))

    add(entry("statistics", strings.statsTitle, "", strings.statsTitle, strings.statsTitle, "Stats", 1400,
        listOf("stats", "statistics", "统计")))
    add(entry("ai_cards", strings.aiCardGeneration, strings.aiCardGenerationDesc, strings.aiCardGeneration, strings.aiCardGeneration, "AICards", 1500,
        listOf("ai", "cards", "卡片", "生成")))
}

private fun entry(
    id: String, title: String, description: String,
    page: String, section: String, route: String, order: Int,
    keywords: List<String> = emptyList(),
) = SettingsIndexEntry(id, title, description, keywords, page, section, route, order)

@Composable
fun SettingsSearchResults(
    query: String,
    results: List<SettingsSearchResult>,
    onResultClick: (SettingsSearchResult) -> Unit,
    strings: com.lumecard.app.i18n.I18nStrings,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = query.isNotBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.settingsSearchNoResults,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(results, key = { it.entry.id }) { result ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(result) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = LumeCardTheme.spacing.md,
                                vertical = LumeCardTheme.spacing.sm,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(LumeCardTheme.spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    result.entry.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (result.entry.description.isNotBlank()) {
                                    Text(
                                        result.entry.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    buildString {
                                        append(result.entry.page)
                                        if (result.entry.section.isNotBlank() && result.entry.section != result.entry.page) {
                                            append(" > ").append(result.entry.section)
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
