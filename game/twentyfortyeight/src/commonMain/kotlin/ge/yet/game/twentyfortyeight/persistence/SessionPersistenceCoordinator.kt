package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.twentyfortyeight.diagnostics.InvariantCode
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
        val onCancellation: (CancellationException) -> Unit,
    )

    private sealed interface Admission {
        data object OwnsWriter : Admission
        data object Queued : Admission
        data class Rejected(val result: CheckpointResult.Failed) : Admission
    }

    private val mutex = Mutex()
    private val state = MutableStateFlow(CoordinatorSnapshot(null, null))
    private var pending: Request? = null
    private var inFlightBarrier: Boolean = false
    private var highestAcceptedRevision: Long? = null

    fun snapshot(): CoordinatorSnapshot = state.value

    suspend fun submit(
        commit: GameCommit,
        onResult: (CheckpointResult) -> Unit,
    ) {
        submit(
            Request(
                commit = commit,
                barrier = false,
                onResult = onResult,
                onCancellation = {},
            ),
        )
    }

    suspend fun commitBeforeVisible(commit: GameCommit): CheckpointResult {
        val result = CompletableDeferred<CheckpointResult>()
        submit(
            Request(
                commit = commit,
                barrier = true,
                onResult = result::complete,
                onCancellation = result::cancel,
            ),
        )
        return result.await()
    }

    private suspend fun submit(request: Request) {
        val admission = mutex.withLock {
            when {
                highestAcceptedRevision?.let { request.commit.revision <= it } == true ->
                    Admission.Rejected(request.failed(InvariantCode.RevisionRegression))
                inFlightBarrier || pending?.barrier == true -> Admission.Rejected(
                    request.failed(InvariantCode.BarrierPending),
                )
                state.value.inFlightRevision == null -> {
                    highestAcceptedRevision = request.commit.revision
                    inFlightBarrier = request.barrier
                    state.value = CoordinatorSnapshot(request.commit.revision, null)
                    Admission.OwnsWriter
                }
                else -> {
                    highestAcceptedRevision = request.commit.revision
                    pending = request
                    state.value = state.value.copy(pendingRevision = request.commit.revision)
                    Admission.Queued
                }
            }
        }
        when (admission) {
            Admission.OwnsWriter -> drain(request)
            Admission.Queued -> Unit
            is Admission.Rejected -> request.onResult(admission.result)
        }
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
                        inFlightBarrier = false
                        CoordinatorSnapshot(null, null)
                    } else {
                        inFlightBarrier = queued.barrier
                        CoordinatorSnapshot(queued.commit.revision, null)
                    }
                    queued
                }
                request.onResult(result)
                current = next
            }
        } catch (cancellation: CancellationException) {
            val queued = mutex.withLock {
                val queued = pending
                pending = null
                inFlightBarrier = false
                state.value = CoordinatorSnapshot(null, null)
                queued
            }
            queued?.onCancellation(cancellation)
            throw cancellation
        }
    }

    private fun Request.failed(code: InvariantCode): CheckpointResult.Failed =
        CheckpointResult.Failed(
            revision = commit.revision,
            failure = TwentyFortyEightFailure.InvariantViolation(code),
        )

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
