package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.twentyfortyeight.diagnostics.StorageOperation
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface CheckpointResult {
    val revision: Long

    data class Stored(override val revision: Long) : CheckpointResult

    data class Failed(
        override val revision: Long,
        val failure: TwentyFortyEightFailure,
    ) : CheckpointResult
}

internal data class CoordinatorSnapshot(
    val inFlightRevision: Long?,
    val pendingRevision: Long?,
) {
    val inFlightCount: Int
        get() = if (inFlightRevision == null) 0 else 1
}

internal class SessionPersistenceCoordinator(
    private val storage: MiniAppStorage,
    private val writer: GameCommitWriter,
) {
    private data class Request(
        val commit: GameCommit,
        val barrier: Boolean,
        val onResult: (CheckpointResult) -> Unit,
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow(CoordinatorSnapshot(null, null))
    private var pending: Request? = null

    fun snapshot(): CoordinatorSnapshot = state.value

    suspend fun submit(
        commit: GameCommit,
        onResult: (CheckpointResult) -> Unit,
    ) {
        submit(Request(commit, barrier = false, onResult = onResult))
    }

    suspend fun commitBeforeVisible(commit: GameCommit): CheckpointResult {
        val result = CompletableDeferred<CheckpointResult>()
        submit(
            Request(
                commit = commit,
                barrier = true,
                onResult = result::complete,
            ),
        )
        return result.await()
    }

    private suspend fun submit(request: Request) {
        val ownsWriter = mutex.withLock {
            if (state.value.inFlightRevision == null) {
                state.value = CoordinatorSnapshot(request.commit.revision, null)
                true
            } else {
                if (pending?.barrier != true || request.barrier) {
                    pending = request
                    state.value = state.value.copy(pendingRevision = request.commit.revision)
                }
                false
            }
        }
        if (ownsWriter) drain(request)
    }

    private suspend fun drain(initial: Request) {
        var current: Request? = initial
        try {
            while (current != null) {
                val request = current
                val result = write(request.commit)
                val next = mutex.withLock {
                    val queued = pending
                    pending = null
                    state.value = if (queued == null) {
                        CoordinatorSnapshot(null, null)
                    } else {
                        CoordinatorSnapshot(queued.commit.revision, null)
                    }
                    queued
                }
                request.onResult(result)
                current = next
            }
        } catch (cancellation: CancellationException) {
            mutex.withLock {
                pending = null
                state.value = CoordinatorSnapshot(null, null)
            }
            throw cancellation
        }
    }

    private suspend fun write(commit: GameCommit): CheckpointResult = try {
        writer.commit(storage, commit)
        CheckpointResult.Stored(commit.revision)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: PersistenceWriteException) {
        CheckpointResult.Failed(commit.revision, failure.failure)
    } catch (_: Exception) {
        CheckpointResult.Failed(
            commit.revision,
            TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite),
        )
    }
}
