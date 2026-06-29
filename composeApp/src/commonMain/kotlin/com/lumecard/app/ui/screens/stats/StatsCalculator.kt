package com.lumecard.app.ui.screens.stats

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.ReviewLog
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

object StatsCalculator {

    data class ForecastBucket(val labelKey: String, val count: Int)
    data class ForecastData(
        val buckets: List<ForecastBucket>,
        val haveBacklog: Boolean,
        val backlogCount: Int
    )

    data class IntervalBucket(val label: String, val minDays: Int, val maxDays: Int, val count: Int)
    data class IntervalData(
        val buckets: List<IntervalBucket>,
        val averageInterval: Double,
        val maxInterval: Int,
        val totalCardsWithInterval: Int
    )

    data class RetentionPeriodData(
        val label: String,
        val youngPassed: Int, val youngFailed: Int,
        val maturePassed: Int, val matureFailed: Int
    ) {
        val youngTotal: Int get() = youngPassed + youngFailed
        val matureTotal: Int get() = maturePassed + matureFailed
        val overallTotal: Int get() = youngTotal + matureTotal
        val youngRate: Double get() = if (youngTotal > 0) youngPassed.toDouble() / youngTotal else 0.0
        val matureRate: Double get() = if (matureTotal > 0) maturePassed.toDouble() / matureTotal else 0.0
        val overallRate: Double
            get() = if (overallTotal > 0) (youngPassed + maturePassed).toDouble() / overallTotal else 0.0
    }
    data class RetentionData(val periods: List<RetentionPeriodData>)

    data class CardCountsData(
        val newCards: Int,
        val learning: Int,
        val young: Int,
        val mature: Int
    ) {
        val total: Int get() = newCards + learning + young + mature
    }

    fun computeForecast(allCards: List<Card>, now: Instant): ForecastData {
        val tz = TimeZone.currentSystemDefault()
        val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz)

        var backlogCount = 0
        val buckets = mutableMapOf<String, Int>()
        buckets["today"] = 0
        buckets["1mo"] = 0
        buckets["3mo"] = 0
        buckets["6mo"] = 0
        buckets["1yr"] = 0
        buckets["all"] = 0

        for (card in allCards) {
            if (card.deletedAt != null) continue
            if (card.lastReviewedAt == null) continue
            val next = card.nextReviewAt ?: continue

            val diffMs = next.toEpochMilliseconds() - todayStart.toEpochMilliseconds()
            val dayOffset = (diffMs / 86_400_000).toInt()

            if (dayOffset < 0) {
                backlogCount++
            } else if (dayOffset == 0) {
                buckets["today"] = buckets["today"]!! + 1
            } else if (dayOffset <= 30) {
                buckets["1mo"] = buckets["1mo"]!! + 1
            } else if (dayOffset <= 90) {
                buckets["3mo"] = buckets["3mo"]!! + 1
            } else if (dayOffset <= 180) {
                buckets["6mo"] = buckets["6mo"]!! + 1
            } else if (dayOffset <= 365) {
                buckets["1yr"] = buckets["1yr"]!! + 1
            } else {
                buckets["all"] = buckets["all"]!! + 1
            }
        }

        val bucketList = listOf(
            ForecastBucket("today", buckets["today"]!!),
            ForecastBucket("1mo", buckets["1mo"]!!),
            ForecastBucket("3mo", buckets["3mo"]!!),
            ForecastBucket("6mo", buckets["6mo"]!!),
            ForecastBucket("1yr", buckets["1yr"]!!),
            ForecastBucket("all", buckets["all"]!!),
        ).filter { it.count > 0 }

        return ForecastData(
            buckets = bucketList,
            haveBacklog = backlogCount > 0,
            backlogCount = backlogCount
        )
    }

    fun computeIntervals(
        allCards: List<Card>,
        allLogs: List<ReviewLog>
    ): IntervalData {
        val cardIntervals = mutableMapOf<String, Int>()
        for (log in allLogs.sortedByDescending { it.reviewedAt }) {
            if (log.cardId !in cardIntervals) {
                cardIntervals[log.cardId] = log.interval
            }
        }

        val intervals = allCards
            .filter { it.deletedAt == null && it.lastReviewedAt != null }
            .mapNotNull { cardIntervals[it.id] }
            .filter { it >= 1 }

        if (intervals.isEmpty()) return IntervalData(emptyList(), 0.0, 0, 0)

        val average = intervals.average()
        val maxInterval = intervals.max()

        val bucketDefs = listOf(
            1 to 1,
            2 to 2,
            3 to 3,
            4 to 4,
            5 to 7,
            8 to 14,
            15 to 30,
            31 to 60,
            61 to 90,
            91 to 180,
            181 to 365,
            366 to Int.MAX_VALUE
        )

        val buckets = bucketDefs.map { (minI, maxI) ->
            val count = intervals.count { it in minI..maxI }
            val label = when {
                minI == 1 && maxI == 1 -> "1d"
                minI == maxI -> "${minI}d"
                maxI == Int.MAX_VALUE -> "${minI}d+"
                else -> "${minI}-${maxI}d"
            }
            IntervalBucket(label, minI, maxI, count)
        }.filter { it.count > 0 }

        return IntervalData(buckets, average, maxInterval, intervals.size)
    }

    fun computeRetention(
        allLogs: List<ReviewLog>,
        now: Instant
    ): RetentionData {
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        val dayStartMs = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val dayMs = 86_400_000L

        val nonDeleted = allLogs.filter { it.deletedAt == null }

        val logsByCard = nonDeleted
            .groupBy { it.cardId }
            .mapValues { (_, logs) -> logs.sortedBy { it.reviewedAt } }

        data class ReviewWithLastIvl(
            val rating: Int,
            val interval: Int,
            val lastInterval: Int,
            val reviewedAtMs: Long
        )

        val reviews = logsByCard.values.flatMap { cardLogs ->
            cardLogs.mapIndexed { i, log ->
                val lastIvl = if (i > 0) cardLogs[i - 1].interval else 0
                ReviewWithLastIvl(log.rating, log.interval, lastIvl, log.reviewedAt.toEpochMilliseconds())
            }
        }

        val qualifying = reviews.filter { r ->
            r.lastInterval >= 1 || r.interval >= 1
        }

        val allDayEndMs = dayStartMs + dayMs

        data class PeriodRange(val label: String, val startMs: Long, val endMs: Long)
        val periods = listOf(
            PeriodRange("today", dayStartMs, allDayEndMs),
            PeriodRange("yesterday", dayStartMs - dayMs, dayStartMs),
            PeriodRange("week", dayStartMs - 7 * dayMs, allDayEndMs),
            PeriodRange("month", dayStartMs - 30 * dayMs, allDayEndMs),
            PeriodRange("year", dayStartMs - 365 * dayMs, allDayEndMs),
            PeriodRange("all_time", 0L, allDayEndMs)
        )

        val periodResults = periods.map { p ->
            var yp = 0; var yf = 0; var mp = 0; var mf = 0
            for (r in qualifying) {
                if (r.reviewedAtMs >= p.startMs && r.reviewedAtMs < p.endMs) {
                    if (r.lastInterval < 21) {
                        if (r.rating == 1) yf++ else yp++
                    } else {
                        if (r.rating == 1) mf++ else mp++
                    }
                }
            }
            RetentionPeriodData(p.label, yp, yf, mp, mf)
        }

        return RetentionData(periodResults)
    }

    fun computeCardCounts(
        allCards: List<Card>,
        allLogs: List<ReviewLog>
    ): CardCountsData {
        val cardIntervals = mutableMapOf<String, Int>()
        for (log in allLogs.sortedByDescending { it.reviewedAt }) {
            if (log.cardId !in cardIntervals) {
                cardIntervals[log.cardId] = log.interval
            }
        }

        var newCards = 0
        var learning = 0
        var young = 0
        var mature = 0

        for (card in allCards) {
            if (card.deletedAt != null) continue
            if (card.nextReviewAt == null) {
                newCards++
            } else if (card.lastReviewedAt == null) {
                learning++
            } else {
                val ivl = cardIntervals[card.id] ?: 0
                if (ivl < 21) young++
                else mature++
            }
        }

        return CardCountsData(newCards, learning, young, mature)
    }
}
