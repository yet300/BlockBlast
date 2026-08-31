package ge.yet.game.fruitmerge.store

import com.arkivanov.mvikotlin.core.store.Store
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.Vec2

internal interface FruitMergeStore :
    Store<FruitMergeStore.Intent, FruitMergeStore.State, FruitMergeStore.Label> {

    sealed interface Intent {
        data class Frame(val elapsedSeconds: Float) : Intent
        data class MovePreview(val x: Float) : Intent
        data object Drop : Intent
        data object BeginFreeClear : Intent
        data class ClearBody(val id: Long, val paid: Boolean) : Intent
        data object CancelClear : Intent
        data object FreeShake : Intent
        data object PaidClear : Intent
        data object PaidShake : Intent
        data object NewGame : Intent
        data object Checkpoint : Intent
        data class VisibilityChanged(val active: Boolean) : Intent
    }

    data class State(
        val game: FruitMergeState = FruitMergeState(),
        val initialized: Boolean = false,
        val active: Boolean = true,
    )

    sealed interface Label {
        data class DropReleased(val level: FruitLevel) : Label
        data class FruitLanded(val level: FruitLevel, val position: Vec2) : Label
        data class MergeResolved(val level: FruitLevel, val position: Vec2) : Label
        data class ClearApplied(val level: FruitLevel, val position: Vec2) : Label
        data object ShakeStarted : Label
        data class ShakePulse(val index: Int) : Label
        data object DangerEntered : Label
        data object ResultReached : Label
    }
}
