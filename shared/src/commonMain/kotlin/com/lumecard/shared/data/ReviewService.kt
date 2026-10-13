package com.lumecard.shared.data

import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.domain.scheduler.AlgorithmState
import com.lumecard.shared.domain.scheduler.AlgorithmStateCodec
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.domain.scheduler.FSRSAlgorithmAdapter
import com.lumecard.shared.domain.scheduler.LeitnerAlgorithm
import com.lumecard.shared.domain.scheduler.ReviewAlgorithm
import com.lumecard.shared.domain.scheduler.ReviewMode
import com.lumecard.shared.domain.scheduler.SM2Algorithm
import com.lumecard.shared.domain.scheduler.SimpleAlgorithm
import com.lumecard.shared.model.PlanStatus
import com.lumecard.shared.model.Rating
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

data class ReviewResult(val cardId: String, val state: AlgorithmState, val reviewedAt: Instant)

/** Database-bound review command. The caller dispatches this synchronous work to IO. */
class ReviewService(private val database: LumeCardDatabase, fsrs: FSRSAlgorithm) {
    private val queries get() = database.lumeCardDatabaseQueries
    private val algorithms: Map<ReviewMode, ReviewAlgorithm> = listOf(
        FSRSAlgorithmAdapter(fsrs), SM2Algorithm(), LeitnerAlgorithm(), SimpleAlgorithm()
    ).associateBy { it.mode }
    private val json = Json { ignoreUnknownKeys = true }

    fun stateForCard(cardId: String): AlgorithmState = database.transactionWithResult {
        val algorithm = configuredAlgorithm()
        val saved = queries.selectAlgorithmState(cardId).executeAsOneOrNull()
        if (saved?.mode == algorithm.mode.name) AlgorithmStateCodec.decode(saved.state_json)
        else algorithm.initCard()
    }

    fun review(cardId: String, rating: Rating, reviewTimeMs: Int, reviewId: String): ReviewResult =
        database.transactionWithResult(noEnclosing = true) {
            require(reviewTimeMs >= 0)
            val card = requireNotNull(queries.selectCardById(cardId).executeAsOneOrNull()) { "Card no longer exists" }
            require(card.deleted_at == null) { "Card was deleted" }
            val algorithm = configuredAlgorithm()
            val saved = queries.selectAlgorithmState(cardId).executeAsOneOrNull()
            val state = if (saved?.mode == algorithm.mode.name) AlgorithmStateCodec.decode(saved.state_json)
                else algorithm.initCard()
            val now = Clock.System.now()
            val elapsedDays = card.last_reviewed_at?.let {
                ((now.toEpochMilliseconds() - Instant.parse(it).toEpochMilliseconds()) / 86_400_000L)
                    .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } ?: 0
            val updated = algorithm.schedule(state, rating, elapsedDays)
            queries.insertReviewLog(reviewId, cardId, rating.value.toLong(), reviewTimeMs.toLong(),
                updated.intervalDays.toLong(), updated.easeFactor.toDouble(), updated.repetitions.toLong(),
                updated.lapses.toLong(), now.toString(), 1L, null, null)
            queries.updateCardScheduling(now.toString(), updated.nextReviewAt.toString(), now.toString(), cardId)
            queries.insertAlgorithmState(cardId, algorithm.mode.name, AlgorithmStateCodec.encode(updated))
            refreshPlans(now, cardId = cardId)
            ReviewResult(cardId, updated, now)
        }

    fun reconcilePlan(planId: String) {
        database.transaction(noEnclosing = true) { refreshPlans(Clock.System.now(), planId = planId) }
    }

    /** For repository mutations already enclosed in a database transaction. */
    internal fun reconcileAllInCurrentTransaction() = refreshPlans(Clock.System.now())

    internal fun reconcilePlanInCurrentTransaction(planId: String) =
        refreshPlans(Clock.System.now(), planId = planId)

    /** A reset has its own review boundary and never changes the cards shared by other plans. */
    fun resetPlanProgress(planId: String) {
        database.transaction(noEnclosing = true) {
            val plan = queries.selectLearningPlanById(planId).executeAsOneOrNull()
                ?.takeIf { it.deleted_at == null } ?: return@transaction
            val now = Clock.System.now().toString()
            queries.insertSetting(PlanProgressKeys.forPlan(planId), now)
            queries.deleteSetting(PlanProgressKeys.manualComplete(planId))
            queries.updateLearningPlanProgress(plan.total_cards, 0L, PlanStatus.NOT_STARTED.name, now, planId)
        }
    }

    /** Manual completion is distinct from reaching the current card total through reviews. */
    fun completePlan(planId: String) {
        database.transaction(noEnclosing = true) {
            val now = Clock.System.now()
            refreshPlans(now, planId = planId)
            val plan = queries.selectLearningPlanById(planId).executeAsOneOrNull()
                ?.takeIf { it.deleted_at == null } ?: return@transaction
            queries.insertSetting(PlanProgressKeys.manualComplete(planId), "true")
            queries.updateLearningPlanProgress(plan.total_cards, plan.total_cards,
                PlanStatus.COMPLETED.name, now.toString(), planId)
        }
    }

    private fun configuredAlgorithm(): ReviewAlgorithm {
        val setting = queries.selectSetting("reviewMode").executeAsOneOrNull()?.setting_value
        val mode = runCatching { ReviewMode.valueOf(setting ?: ReviewMode.FSRS.name) }
            .getOrDefault(ReviewMode.FSRS)
        return algorithms.getValue(mode)
    }

    private fun refreshPlans(now: Instant, cardId: String? = null, planId: String? = null) {
        val plans = queries.selectAllLearningPlans().executeAsList()
            .filter { planId == null || it.id == planId }
        if (plans.isEmpty()) return
        val cards = queries.selectAllCards().executeAsList().map { PlanCardRef(it.id, it.deck_id) }
        val decks = queries.selectAllDecks().executeAsList().map { PlanDeckRef(it.id, it.knowledge_base_id) }
        val membersByPlan = plans.mapNotNull { plan ->
            val selection = PlanSelection(
                json.decodeFromString(plan.knowledge_base_ids ?: "[]"),
                json.decodeFromString(plan.deck_ids ?: "[]"),
                json.decodeFromString(plan.card_ids ?: "[]"),
            )
            val members = LearningPlanCardResolver.cardIds(selection, cards, decks)
            if (cardId != null && cardId !in members) null else plan to members
        }
        if (membersByPlan.isEmpty()) return
        val latestReviewByCard = mutableMapOf<String, Instant>()
        queries.selectActiveReviewTimestamps().executeAsList().forEach { log ->
            val reviewedAt = Instant.parse(log.reviewed_at)
            val previous = latestReviewByCard[log.card_id]
            if (previous == null || reviewedAt > previous) latestReviewByCard[log.card_id] = reviewedAt
        }
        val settings = queries.selectAllSettings().executeAsList().associate { it.key to it.setting_value }
        membersByPlan.forEach { (plan, members) ->
            val resetBoundary = settings[PlanProgressKeys.forPlan(plan.id)]?.let { Instant.parse(it) }
            val boundary = resetBoundary ?: Instant.parse(plan.created_at)
            val total = members.size.toLong()
            val completed = members.count { memberId ->
                val reviewedAt = latestReviewByCard[memberId]
                reviewedAt != null && (if (resetBoundary == null) reviewedAt >= boundary else reviewedAt > boundary)
            }.toLong()
            val manuallyCompleted = settings[PlanProgressKeys.manualComplete(plan.id)] == "true"
            val status = when {
                manuallyCompleted -> PlanStatus.COMPLETED
                total > 0L && completed >= total -> PlanStatus.COMPLETED
                total == 0L -> PlanStatus.NOT_STARTED
                completed > 0L || plan.status == PlanStatus.IN_PROGRESS.name -> PlanStatus.IN_PROGRESS
                else -> PlanStatus.NOT_STARTED
            }
            val finalCompleted = if (manuallyCompleted) total else completed
            if (total != plan.total_cards || finalCompleted != plan.completed_cards || status.name != plan.status) {
                queries.updateLearningPlanProgress(total, finalCompleted, status.name, now.toString(), plan.id)
            }
        }
    }
}
