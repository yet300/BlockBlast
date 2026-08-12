package ge.yet.blockblast.feature.game.store

import ge.yet.game.domain.model.GameState
import ge.yet.game.domain.repository.AnalyticRepository

internal class GameAnalyticsLogger(private val analytics: AnalyticRepository) {

    fun log(
        eventName: String,
        state: GameState,
        extra: Map<String, Any> = emptyMap(),
    ) {
        analytics.logEvent(
            eventName = eventName,
            params = gameParams(state) + extra,
        )
    }

    private fun gameParams(state: GameState): Map<String, Any> = mapOf(
        "score" to state.score,
        "best_score" to state.bestScore,
        "revives_used" to state.revivesUsed,
        "remaining_pieces" to state.currentPieces.size,
    )
}
