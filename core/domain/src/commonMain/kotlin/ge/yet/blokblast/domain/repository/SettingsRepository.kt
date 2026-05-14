package ge.yet.blokblast.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val soundEnabled: StateFlow<Boolean>
    val vibrationEnabled: StateFlow<Boolean>
    val darkTheme: StateFlow<Boolean>

    /** Persisted personal best, survives across launches. */
    val bestScore: StateFlow<Long>

    /** How many times the in-app review prompt has been shown to this user. */
    val reviewPromptCount: StateFlow<Int>

    /** Whether the user has seen (or dismissed) the first-launch tutorial. */
    val tutorialSeen: StateFlow<Boolean>

    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setDarkTheme(enabled: Boolean)

    /** Monotonic write: implementations must ignore scores ≤ current best. */
    suspend fun setBestScore(score: Long)

    /** Increment the lifetime review-prompt counter by one. */
    suspend fun incrementReviewPromptCount()

    /**
     * Cap the review-prompt counter at [max] so no further prompts ever fire.
     * Idempotent: no-op when the counter is already at or above [max].
     */
    suspend fun suppressReviewPrompts(max: Int)

    /** Mark the first-launch tutorial as seen so it does not re-appear. */
    suspend fun setTutorialSeen()
}
