package ge.yet.game.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository : FeedbackPreferences {
    /** Background music gate. Independent of [sfxEnabled] since v1.5.0. */
    val musicEnabled: StateFlow<Boolean>

    /** Sound-effects and voice-line gate, independent of [musicEnabled]. */
    override val sfxEnabled: StateFlow<Boolean>

    override val vibrationEnabled: StateFlow<Boolean>
    val darkTheme: StateFlow<Boolean>
    val adsEnabled: StateFlow<Boolean>

    suspend fun setMusicEnabled(enabled: Boolean)
    suspend fun setSfxEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setAdsEnabled(enabled: Boolean)
}
