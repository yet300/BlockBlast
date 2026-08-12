package ge.yet.blockblast.feature.game

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import ge.yet.blockblast.feature.game.tray.PieceTrayComponent
import ge.yet.game.domain.model.GameState

/**
 * In-game screen. Delegates gameplay transitions to its retained `GameStore`; the component
 * is just a thin adapter that turns UI intents into engine calls and mirrors
 * engine state to the UI.
 *
 * Settings is reachable directly from Game via [onSettingsClicked].
 */
interface GameComponent {

    val model: Value<Model>

    val pieceTray: PieceTrayComponent

    data class Model(
        val game: GameState,
    )

    fun onCellClicked(pieceId: Long, x: Int, y: Int)
    fun onReviveClicked()
    fun onRestartClicked()
    fun onSettingsClicked()
    fun onExitClicked()


    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            isNewGame: Boolean,
            restoredResultState: GameState?,
            onSettingsClicked: () -> Unit,
            onExitClicked: () -> Unit,
            onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
            onReviveCompleted: (GameState) -> Unit,
            onReviveFailed: () -> Unit,
        ): GameComponent
    }

}
