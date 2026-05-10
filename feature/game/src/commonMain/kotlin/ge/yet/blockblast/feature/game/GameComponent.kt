package ge.yet.blockblast.feature.game

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import ge.yet.blockblast.feature.settings.SettingsComponent
import ge.yet.blokblast.domain.model.GameState

/**
 * In-game screen. Delegates all logic to the shared `GameEngine`; the component
 * is just a thin adapter that turns UI intents into engine calls and mirrors
 * engine state to the UI.
 *
 * Settings is reachable directly from Game via [onSettingsClicked].
 */
interface GameComponent {

    val model: Value<GameState>

    /**
     * Seconds remaining on the Game Over "Continue" button. Starts at
     * [CONTINUE_COUNTDOWN_SECONDS] the moment the game ends and counts down to
     * zero. The sentinel [COUNTDOWN_INACTIVE] (= -1) is emitted whenever the
     * game is not in the game-over state. Owned by the component so it
     * survives configuration changes and is unit-testable.
     *
     * Decompose's `Value<T>` requires `T : Any`, which is why we use a sentinel
     * instead of `Value<Int?>`.
     */
    val continueCountdown: Value<Int>

    val sheetSlot: Value<ChildSlot<*, SheetChild>>

    fun onCellClicked(pieceId: Long, x: Int, y: Int)
    fun onReviveClicked()
    fun onRestartClicked()
    fun onSettingsClicked()
    fun onExitClicked()

    fun onDismissSheet()

    sealed interface SheetChild {
        class Settings(
            val component: SettingsComponent,
        ) : SheetChild
    }

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            isNewGame: Boolean,
            onExitClicked: () -> Unit,
        ): GameComponent
    }

}
