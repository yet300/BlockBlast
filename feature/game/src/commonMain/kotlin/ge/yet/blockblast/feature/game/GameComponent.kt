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
            onExitClicked: () -> Unit,
        ): GameComponent
    }
}
