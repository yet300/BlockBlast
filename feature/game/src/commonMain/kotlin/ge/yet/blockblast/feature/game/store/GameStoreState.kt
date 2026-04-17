package ge.yet.blockblast.feature.game.store

import ge.yet.blokblast.domain.model.GameState

/**
 * Combined store state.
 *
 * Wraps the pure-domain [GameState] together with UI-adjacent state that the
 * component layer also needs but that doesn't belong in the domain model —
 * namely the Game Over "Continue" button countdown.
 *
 * The domain model stays free of ad/UI concerns; the countdown is driven by
 * [GameStoreFactory]'s executor on top of engine-state transitions.
 */
internal data class GameStoreState(
    val game: GameState,
    /**
     * Seconds remaining on the Game Over Continue button. `-1` means no
     * countdown is active (game is not in the game-over state). `0` means
     * the countdown has expired and the UI should morph the button into
     * "New game".
     */
    val continueCountdown: Int,
) {
    companion object {
        const val COUNTDOWN_INACTIVE: Int = -1
        const val CONTINUE_COUNTDOWN_SECONDS: Int = 5
    }
}
