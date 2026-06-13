package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.Rating
import kotlinx.datetime.Instant

enum class ReviewMode(val displayName: String, val description: String) {
    FSRS("FSRS", "新一代间隔重复算法，根据历史数据优化复习时间"),
    SM2("SM-2", "Anki常用算法，根据记忆表现动态调整复习间隔"),
    LEITNER("莱特纳盒子", "分箱机制，答对进入下一层，答错返回第一层"),
    SIMPLE("简单模式", "固定间隔复习：1天→3天→7天→15天→30天")
}

data class AlgorithmState(
    val intervalDays: Int = 0,
    val nextReviewAt: Instant = Instant.DISTANT_FUTURE,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val easeFactor: Float = 2.5f,
    val stage: Int = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0
)

interface ReviewAlgorithm {
    val mode: ReviewMode
    fun initCard(): AlgorithmState
    fun schedule(state: AlgorithmState, rating: Rating): AlgorithmState
}
