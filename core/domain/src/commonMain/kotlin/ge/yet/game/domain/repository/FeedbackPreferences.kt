package ge.yet.game.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface FeedbackPreferences {
    val sfxEnabled: StateFlow<Boolean>
    val vibrationEnabled: StateFlow<Boolean>
}
