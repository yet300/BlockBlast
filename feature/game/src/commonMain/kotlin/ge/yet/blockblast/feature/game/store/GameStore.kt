package ge.yet.blockblast.feature.game.store

import com.arkivanov.mvikotlin.core.store.Store
import ge.yet.blokblast.domain.model.GameState

internal interface GameStore : Store<GameStore.Intent, GameStoreState, Nothing> {

    sealed interface Intent {
        data object Start : Intent
        data class Place(val pieceId: Long, val x: Int, val y: Int) : Intent
        data object Revive : Intent
        data object Restart : Intent
    }

    sealed interface Action {
        data object Init : Action
    }

    sealed interface Msg {
        data class Snapshot(val state: GameState) : Msg
        data class CountdownTick(val secondsRemaining: Int) : Msg
    }

}
