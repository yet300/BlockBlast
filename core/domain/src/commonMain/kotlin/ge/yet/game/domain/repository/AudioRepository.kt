package ge.yet.game.domain.repository

/** Implementations MUST consult [SettingsRepository.sfxEnabled] before playing sounds. */
interface AudioRepository {
    fun playSound(filename: String)

    /** Starts looping background music. Safe to call multiple times. */
    fun startMusic(tracks: List<String>)

    /** Stops background music immediately and clears the session flag. */
    fun stopMusic()

    /**
     * App moved to background (home button, incoming call, app switcher).
     * Pauses audio without clearing the session flag so [onAppForeground]
     * can transparently resume it.
     */
    fun onAppBackground()

    /**
     * App returned to foreground.
     * Resumes background music if a game session is active.
     */
    fun onAppForeground()
}
