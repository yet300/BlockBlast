package ge.yet.game.blockblast.component.game.store

import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes saves for one Store without owning a coroutine lifecycle. */
internal class GameSaveCoordinator(
    private val repository: GameSaveRepository,
    private val debounceMillis: Long = 300L,
) {
    private val mutex = Mutex()
    private var generation = 0L
    private var pendingSave: Job? = null

    fun schedule(
        scope: CoroutineScope,
        state: GameState,
        onFailure: (Exception) -> Unit = {},
    ) {
        generation += 1
        val scheduledGeneration = generation
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(debounceMillis)
            try {
                mutex.withLock {
                    if (scheduledGeneration == generation) repository.save(state)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onFailure(error)
            }
        }
    }

    suspend fun flush(state: GameState) {
        generation += 1
        val pending = pendingSave
        pendingSave = null
        pending?.cancelAndJoin()
        mutex.withLock {
            repository.save(state)
        }
    }
}
