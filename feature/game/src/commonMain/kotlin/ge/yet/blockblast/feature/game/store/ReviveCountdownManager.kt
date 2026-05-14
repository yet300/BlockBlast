package ge.yet.blockblast.feature.game.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the revive countdown lifecycle: starts a ticking job, cancels the
 * previous one on retrigger, and surfaces ticks/expiration to the caller via
 * callbacks. Stateful (holds the current Job), so create one instance per
 * Store, not per tick.
 */
internal class ReviveCountdownManager(
    private val onTick: (Int) -> Unit,
    private val onExpired: () -> Unit,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            var seconds = GameStoreState.CONTINUE_COUNTDOWN_SECONDS
            onTick(seconds)
            while (seconds > 0) {
                delay(1000)
                seconds -= 1
                onTick(seconds)
            }
            onExpired()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        onTick(GameStoreState.COUNTDOWN_INACTIVE)
    }
}
