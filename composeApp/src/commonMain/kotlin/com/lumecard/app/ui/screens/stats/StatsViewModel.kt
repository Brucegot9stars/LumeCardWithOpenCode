package com.lumecard.app.ui.screens.stats

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.ReviewLogRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

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
                val allCards = cardRepository.getAll().first()
                val allDecks = deckRepository.getAll().first()
                val reviewStats = reviewLogRepository.getStats()
                val allLogs = reviewLogRepository.getAll().first()

                val now = Clock.System.now()
                val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

                val weekStart = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
                val monthStart = LocalDate(today.year, today.month, 1)

                val todayLogs = allLogs.filter {
                    it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == today
                }
                val weekLogs = allLogs.filter {
                    it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date >= weekStart
                }
                val monthLogs = allLogs.filter {
                    it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date >= monthStart
                }

                var streak = 0
                var checkDate = today
                val reviewDates = allLogs.map {
                    it.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }.toSet()
                while (reviewDates.contains(checkDate)) {
                    streak++
                    checkDate = checkDate.minus(1, DateTimeUnit.DAY)
                }

                val dailyGoal = settingsRepository.getInt("dailyGoal", 20)
                val newCardsPerDayGoal = settingsRepository.getInt("newCardsPerDay", 20)

                val todayNewCards = allCards.filter { it.deletedAt == null }.count {
                    val next = it.nextReviewAt
                    next == null && it.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == today
                }

                val nonDeletedCards = allCards.filter { it.deletedAt == null }
                val forecast = StatsCalculator.computeForecast(nonDeletedCards, now)
                val intervals = StatsCalculator.computeIntervals(nonDeletedCards, allLogs)
                val retention = StatsCalculator.computeRetention(allLogs, now)
                val cardCounts = StatsCalculator.computeCardCounts(nonDeletedCards, allLogs)

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
            }
        }
    }
}
