package ge.yet.game.domain.repository

/** Implementations MUST consult [SettingsRepository.vibrationEnabled] before vibrating. */
interface VibrationRepository {
    suspend fun vibrateLight()
    suspend fun vibrateHeavy()
}
