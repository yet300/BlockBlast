package ge.yet.blockblast.feature.game.store

import com.arkivanov.mvikotlin.core.store.Store
import ge.yet.blokblast.domain.model.GameState

internal interface GameStore : Store<GameStore.Intent, GameStoreState, GameStore.Label> {

    sealed interface Intent {
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

    /**
     * One-shot effects emitted by the executor. Do not put navigation,
     * dialogs, or external SDK calls in [Msg] / state — they belong here so
     * they don't replay on resubscription. Per the mvikotlin-code skill.
     */
    sealed interface Label {
        /** Trigger the platform in-app review flow. The component decides where/how. */
        data object RequestReview : Label
    }
}
