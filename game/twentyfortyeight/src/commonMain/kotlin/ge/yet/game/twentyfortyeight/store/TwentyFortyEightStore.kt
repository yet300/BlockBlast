package ge.yet.game.twentyfortyeight.store

import com.arkivanov.mvikotlin.core.store.Store
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.twentyfortyeight.analytics.AnalyticsFact
import ge.yet.game.twentyfortyeight.audio.AudioEvent
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import ge.yet.game.twentyfortyeight.engine.AudioControls
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.GameState
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.MoveResult
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.engine.UndoTransition

internal interface TwentyFortyEightStore :
    Store<TwentyFortyEightStore.Intent, TwentyFortyEightStore.State, TwentyFortyEightStore.Label> {

    sealed interface Intent {
        data class Move(val direction: Direction) : Intent
        data object Undo : Intent
        data object RequestRestart : Intent
        data object ConfirmRestart : Intent
        data object CancelOverlay : Intent
        data object ContinueAfterVictory : Intent
        data object NewGameFromResult : Intent
        data object SkipTutorial : Intent
        data class AnimationCompleted(val transitionId: Long) : Intent
        data class VisibilityChanged(val visibility: MiniAppVisibility) : Intent
    }

    data class State(
        val bootstrap: BootstrapState = BootstrapState.Loading,
        val game: GameState? = null,
        val statistics: GameStatistics = GameStatistics(),
        val tutorialSeen: Boolean = false,
        val activeTransition: VisualTransition? = null,
        val pendingDirection: Direction? = null,
        val overlay: OverlayState? = null,
        val visibility: MiniAppVisibility,
        val requestedRevision: Long = 0L,
        val durableRevision: Long = 0L,
        val persistenceDirty: Boolean = false,
    )

    sealed interface Label {
        data class NavigateToResult(val snapshot: ResultSnapshot) : Label
        data class NewGameCommitted(val runOrdinal: Long) : Label
        data object AudioStart : Label
        data class AudioControlsChanged(val controls: AudioControls) : Label
        data class Audio(val event: AudioEvent) : Label
        data class Analytics(val fact: AnalyticsFact) : Label
        data class Review(val opportunity: MiniAppReviewOpportunity) : Label
        data class Announcement(val message: AnnouncementFact) : Label
        data class Focus(val target: FocusTarget) : Label
        data class TransientError(val code: UiErrorCode) : Label
        data class Diagnostic(val failure: TwentyFortyEightFailure) : Label
    }
}

internal enum class BootstrapState { Loading, Ready }

internal enum class FocusTarget { Board, Victory, Result }

internal sealed interface OverlayState {
    data object Victory : OverlayState
    data object RestartConfirmation : OverlayState
}

internal sealed interface VisualTransition {
    val transitionId: Long

    data class Move(
        override val transitionId: Long,
        val result: MoveResult.Changed,
    ) : VisualTransition

    data class Undo(
        override val transitionId: Long,
        val transition: UndoTransition,
    ) : VisualTransition
}

internal enum class UiErrorCode { ProgressNotSaved, NewGameNotSaved }

internal sealed interface AnnouncementFact {
    data class Move(val scoreDelta: Long, val largestMerge: Long?) : AnnouncementFact
    data class NewBest(val value: Long) : AnnouncementFact
    data object Victory : AnnouncementFact
    data object GameOver : AnnouncementFact
}
