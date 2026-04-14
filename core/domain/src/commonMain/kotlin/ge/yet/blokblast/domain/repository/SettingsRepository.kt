package ge.yet.blokblast.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val soundEnabled: StateFlow<Boolean>
    val vibrationEnabled: StateFlow<Boolean>
    val darkTheme: StateFlow<Boolean>

    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setDarkTheme(enabled: Boolean)
}
