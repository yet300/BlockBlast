package ge.yet.blockblast.feature.home.store

import ge.yet.blokblast.domain.repository.AnalyticRepository

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
