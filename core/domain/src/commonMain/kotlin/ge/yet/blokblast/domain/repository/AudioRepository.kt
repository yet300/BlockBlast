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

    /** Stops background music immediately and clears the session flag. */
    suspend fun stopMusic()

    /**
     * App moved to background (home button, incoming call, app switcher).
     * Pauses audio without clearing the session flag so [onAppForeground]
     * can transparently resume it.
     */
    suspend fun onAppBackground()

    /**
     * App returned to foreground.
     * Resumes background music if a game session is active.
     */
    suspend fun onAppForeground()
}
