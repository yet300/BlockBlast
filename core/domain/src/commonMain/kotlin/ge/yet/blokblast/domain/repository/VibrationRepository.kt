package ge.yet.blokblast.domain.repository

/** Implementations MUST consult [SettingsRepository.vibrationEnabled] before vibrating. */
interface VibrationRepository {
    suspend fun vibrateLight()
    suspend fun vibrateHeavy()
}
