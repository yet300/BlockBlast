package ge.yet.game.blockblast.component.game

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import ge.yet.game.blockblast.component.tray.PieceTrayComponent
import ge.yet.game.blockblast.domain.model.GameState
import kotlinx.coroutines.flow.StateFlow


interface GameComponent {

    val model: Value<Model>

    val pieceTray: PieceTrayComponent

    val tutorialSeen: StateFlow<Boolean>

    data class Model(
        val game: GameState,
    )

    fun onCellClicked(pieceId: Long, x: Int, y: Int)
    fun onReviveClicked()
    fun onRestartClicked()
    fun onSettingsClicked()
    fun onExitClicked()
    fun onTutorialSeen()


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
