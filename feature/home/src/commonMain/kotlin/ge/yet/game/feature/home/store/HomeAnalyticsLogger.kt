package ge.yet.game.feature.home.store

import ge.yet.game.domain.repository.AnalyticRepository

internal class HomeAnalyticsLogger(private val analytics: AnalyticRepository) {

    fun log(eventName: String, hasSavedGame: Boolean) {
        analytics.logEvent(
            eventName = eventName,
            params = mapOf(
                "has_saved_game" to hasSavedGame,
            ),
        )
    }
}
