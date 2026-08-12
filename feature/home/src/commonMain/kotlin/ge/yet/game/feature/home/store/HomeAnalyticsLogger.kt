package ge.yet.game.feature.home.store

import ge.yet.game.domain.repository.AnalyticRepository

internal class HomeAnalyticsLogger(private val analytics: AnalyticRepository) {

    fun log(eventName: String, bestScore: Long, hasSavedGame: Boolean) {
        analytics.logEvent(
            eventName = eventName,
            params = mapOf(
                "best_score" to bestScore,
                "has_saved_game" to hasSavedGame,
            ),
        )
    }
}
