package ge.yet.blokblast.domain.repository

import ge.yet.blokblast.domain.model.FeedbackType


/** Implementations MUST consult [SettingsRepository.soundEnabled] before playing. */
interface AudioRepository {
    suspend fun playPlacementSound()
    suspend fun playClearSound(lines: Int)
    suspend fun playVoiceFeedback(type: FeedbackType)
    suspend fun playVoiceCombo(combo: Int)

    /** Starts looping background music. Safe to call multiple times. */
    suspend fun startMusic()

    /** Stops background music immediately. */
    suspend fun stopMusic()
}
