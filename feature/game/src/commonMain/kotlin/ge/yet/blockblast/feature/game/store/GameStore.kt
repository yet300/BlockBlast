package ge.yet.blockblast.feature.game.store

import com.arkivanov.mvikotlin.core.store.Store
import ge.yet.game.domain.model.GameState

internal interface GameStore : Store<GameStore.Intent, GameState, GameStore.Label> {

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
    }

    /**
     * One-shot effects emitted by the executor. Do not put navigation,
     * dialogs, or external SDK calls in [Msg] / state — they belong here so
     * they don't replay on resubscription. Per the mvikotlin-code skill.
     */
    sealed interface Label {
        data class GameCompleted(
            val finalState: GameState,
            val canContinue: Boolean,
            val shouldRequestReview: Boolean,
        ) : Label

        data class ReviveCompleted(
            val playableState: GameState,
        ) : Label

        data object ReviveFailed : Label
    }
}
