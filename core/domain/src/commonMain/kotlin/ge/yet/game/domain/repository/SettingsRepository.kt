package ge.yet.game.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    /** Background music gate. Independent of [sfxEnabled] since v1.5.0. */
    val musicEnabled: StateFlow<Boolean>

    /** SFX + voice-line gate (piece placement, line clear, combo voice). */
    val sfxEnabled: StateFlow<Boolean>

    val vibrationEnabled: StateFlow<Boolean>
    val darkTheme: StateFlow<Boolean>
    val adsEnabled: StateFlow<Boolean>

    suspend fun setMusicEnabled(enabled: Boolean)
    suspend fun setSfxEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setAdsEnabled(enabled: Boolean)
}
