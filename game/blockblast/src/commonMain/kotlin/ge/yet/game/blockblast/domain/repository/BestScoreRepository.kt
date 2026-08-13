package ge.yet.game.blockblast.domain.repository

import kotlinx.coroutines.flow.StateFlow

internal interface BestScoreRepository {
    val bestScore: StateFlow<Long>

    /** Persists [score] only when it exceeds the current best score. */
    suspend fun setBestScore(score: Long)
}
