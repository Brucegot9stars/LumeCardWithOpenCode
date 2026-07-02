package com.lumecard.app.ui.screens.stats

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.ReviewLogRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private data class LoadStatsResult(
    val todayLogs: List<*>,
    val weekLogs: List<*>,
    val monthLogs: List<*>,
    val streak: Int,
    val dailyGoal: Int,
    val newCardsPerDayGoal: Int,
    val todayNewCards: Int,
    val nonDeletedCards: List<*>,
    val forecast: StatsCalculator.ForecastData,
    val intervals: StatsCalculator.IntervalData,
    val retention: StatsCalculator.RetentionData,
    val cardCounts: StatsCalculator.CardCountsData,
)

data class AppStats(
    val totalCards: Int = 0,
    val totalDecks: Int = 0,
    val todayReviews: Int = 0,
    val todayNewCards: Int = 0,
    val weekReviews: Int = 0,
    val monthReviews: Int = 0,
    val totalReviews: Int = 0,
    val retentionRate: Double = 0.0,
    val studyTimeMinutes: Int = 0,
    val streakDays: Int = 0,
    val dailyGoal: Int = 20,
    val newCardsPerDayGoal: Int = 20,
    val forecast: StatsCalculator.ForecastData = StatsCalculator.ForecastData(
        emptyList(), false, 0
    ),
    val intervals: StatsCalculator.IntervalData = StatsCalculator.IntervalData(
        emptyList(), 0.0, 0, 0
    ),
    val retention: StatsCalculator.RetentionData = StatsCalculator.RetentionData(
        emptyList()
    ),
    val cardCounts: StatsCalculator.CardCountsData = StatsCalculator.CardCountsData(
        0, 0, 0, 0
    )
)

class StatsViewModel(
    private val reviewLogRepository: ReviewLogRepository,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    private val _stats = MutableStateFlow(AppStats())
    val stats: StateFlow<AppStats> = _stats.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        screenModelScope.launch {
            try {
                val allCards = withContext(Dispatchers.IO) { cardRepository.getAll().first() }
                val allDecks = withContext(Dispatchers.IO) { deckRepository.getAll().first() }
                val reviewStats = withContext(Dispatchers.IO) { reviewLogRepository.getStats() }
                val allLogs = withContext(Dispatchers.IO) { reviewLogRepository.getAll().first() }

                val (todayLogs, weekLogs, monthLogs, streak, dailyGoal, newCardsPerDayGoal, todayNewCards, nonDeletedCards, forecast, intervals, retention, cardCounts) = withContext(Dispatchers.Default) {
                    val now = Clock.System.now()
                    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val weekStart = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
                    val monthStart = LocalDate(today.year, today.month, 1)

                    val tLogs = allLogs.filter { it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == today }
                    val wLogs = allLogs.filter { it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date >= weekStart }
                    val mLogs = allLogs.filter { it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date >= monthStart }

                    var s = 0
                    var cd = today
                    val reviewDates = allLogs.map { it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date }.toSet()
                    while (reviewDates.contains(cd)) { s++; cd = cd.minus(1, DateTimeUnit.DAY) }

                    val ndc = allCards.filter { it.deletedAt == null }
                    val tnc = ndc.count { it.nextReviewAt == null && it.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == today }

                    LoadStatsResult(
                        tLogs, wLogs, mLogs, s,
                        settingsRepository.getInt("dailyGoal", 20),
                        settingsRepository.getInt("newCardsPerDay", 20),
                        tnc, ndc,
                        StatsCalculator.computeForecast(ndc, now),
                        StatsCalculator.computeIntervals(ndc, allLogs),
                        StatsCalculator.computeRetention(allLogs, now),
                        StatsCalculator.computeCardCounts(ndc, allLogs),
                    )
                }

                _stats.value = AppStats(
                    totalCards = nonDeletedCards.size,
                    totalDecks = allDecks.count { it.deletedAt == null },
                    todayReviews = todayLogs.size,
                    todayNewCards = todayNewCards,
                    weekReviews = weekLogs.size,
                    monthReviews = monthLogs.size,
                    totalReviews = allLogs.size,
                    retentionRate = reviewStats.retentionRate * 100.0,
                    studyTimeMinutes = reviewStats.studyTimeMinutes,
                    streakDays = streak,
                    dailyGoal = dailyGoal,
                    newCardsPerDayGoal = newCardsPerDayGoal,
                    forecast = forecast,
                    intervals = intervals,
                    retention = retention,
                    cardCounts = cardCounts
                )
            } catch (e: Exception) {
                println("[LumeCard] StatsViewModel.loadStats failed: ${e.message}")
            }
        }
    }
}
