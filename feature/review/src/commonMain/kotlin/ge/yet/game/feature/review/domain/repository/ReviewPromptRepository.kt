package ge.yet.game.feature.review.domain.repository

import kotlinx.coroutines.flow.StateFlow

internal interface ReviewPromptRepository {
    val promptCount: StateFlow<Int>

    suspend fun incrementPromptCount()

    suspend fun suppressPrompts(max: Int)
}
