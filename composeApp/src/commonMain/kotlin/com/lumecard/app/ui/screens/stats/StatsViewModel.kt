package com.lumecard.app.ui.screens.stats

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.ReviewLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class AppStats(
    val totalCards: Int = 0,
    val totalDecks: Int = 0,
    val newCardsCount: Int = 0,
    val dueCardsCount: Int = 0,
    val upcomingCardsCount: Int = 0,
    val todayReviews: Int = 0,
    val weekReviews: Int = 0,
    val monthReviews: Int = 0,
    val totalReviews: Int = 0,
    val retentionRate: Double = 0.0,
    val studyTimeMinutes: Int = 0,
    val streakDays: Int = 0
)

class StatsViewModel(
    private val reviewLogRepository: ReviewLogRepository,
    private val cardRepository: CardRepository
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
                val reviewStats = reviewLogRepository.getStats()
                val allLogs = reviewLogRepository.getAll().first()

                val now = Clock.System.now()
                val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

                val newCards = allCards.count { it.nextReviewAt == null }
                val dueCards = allCards.count { card ->
                    val next = card.nextReviewAt
                    next != null && next <= now
                }
                val upcomingCards = allCards.count { card ->
                    val next = card.nextReviewAt
                    next != null && next > now
                }
                val weekStart = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
                val monthStart = LocalDate(today.year, today.monthNumber, 1)

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

                _stats.value = AppStats(
                    totalCards = allCards.size,
                    totalDecks = allCards.distinctBy { it.deckId }.size,
                    newCardsCount = newCards,
                    dueCardsCount = dueCards,
                    upcomingCardsCount = upcomingCards,
                    todayReviews = todayLogs.size,
                    weekReviews = weekLogs.size,
                    monthReviews = monthLogs.size,
                    totalReviews = allLogs.size,
                    retentionRate = reviewStats.retentionRate * 100.0,
                    studyTimeMinutes = reviewStats.studyTimeMinutes,
                    streakDays = streak
                )
            } catch (e: Exception) {
                // Keep default zeros
            }
        }
    }
}
