package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.testkit.NoopMiniAppStorage
import ge.yet.game.twentyfortyeight.diagnostics.StorageOperation
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPersistenceCoordinatorTest {
    @Test
    fun `one write runs and pending checkpoint is latest wins`() = runTest {
        val writer = ControlledCommitWriter()
        val results = mutableListOf<CheckpointResult>()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)

        backgroundScope.launch { coordinator.submit(gameCommit(1L), results::add) }
        writer.awaitStarted(1L)
        coordinator.submit(gameCommit(2L), results::add)
        coordinator.submit(gameCommit(3L), results::add)
        runCurrent()

        assertEquals(listOf(1L), writer.startedRevisions)
        assertEquals(1, coordinator.snapshot().inFlightCount)
        assertEquals(3L, coordinator.snapshot().pendingRevision)

        writer.complete(1L)
        writer.awaitStarted(3L)
        writer.complete(3L)
        runCurrent()

        assertEquals(listOf(1L, 3L), writer.startedRevisions)
        assertEquals(listOf(1L, 3L), results.filterIsInstance<CheckpointResult.Stored>().map { it.revision })
        assertEquals(CoordinatorSnapshot(null, null), coordinator.snapshot())
    }

    @Test
    fun `ordinary failure is reported once and no retry loop starts`() = runTest {
        val writer = ControlledCommitWriter(failRevisions = setOf(4L), initiallyOpen = true)
        val results = mutableListOf<CheckpointResult>()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)

        coordinator.submit(gameCommit(4L), results::add)

        assertEquals(listOf(4L), writer.startedRevisions)
        assertEquals<List<CheckpointResult>>(
            listOf(
                CheckpointResult.Failed(
                    4L,
                    TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite),
                ),
            ),
            results,
        )
        assertEquals(CoordinatorSnapshot(null, null), coordinator.snapshot())
    }

    @Test
    fun `destructive barrier waits for its exact durable revision`() = runTest {
        val writer = ControlledCommitWriter()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)
        backgroundScope.launch { coordinator.submit(gameCommit(5L), onResult = {}) }
        writer.awaitStarted(5L)

        val barrier = async { coordinator.commitBeforeVisible(gameCommit(6L)) }
        runCurrent()
        assertFalse(barrier.isCompleted)
        assertEquals(6L, coordinator.snapshot().pendingRevision)

        writer.complete(5L)
        writer.awaitStarted(6L)
        assertFalse(barrier.isCompleted)
        writer.complete(6L)

        assertEquals(CheckpointResult.Stored(6L), barrier.await())
    }

    @Test
    fun `cancellation escapes and clears pending work`() = runTest {
        val writer = ControlledCommitWriter()
        val results = mutableListOf<CheckpointResult>()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)
        val active = launch { coordinator.submit(gameCommit(1L), results::add) }
        writer.awaitStarted(1L)
        coordinator.submit(gameCommit(2L), results::add)

        active.cancelAndJoin()

        assertTrue(results.isEmpty())
        assertEquals(CoordinatorSnapshot(null, null), coordinator.snapshot())
        assertEquals(listOf(1L), writer.startedRevisions)
    }
}

private class ControlledCommitWriter(
    private val failRevisions: Set<Long> = emptySet(),
    private val initiallyOpen: Boolean = false,
) : GameCommitWriter {
    val startedRevisions = mutableListOf<Long>()
    private val started = Channel<Long>(Channel.UNLIMITED)
    private val gates = mutableMapOf<Long, CompletableDeferred<Unit>>()

    override suspend fun commit(storage: MiniAppStorage, commit: GameCommit) {
        startedRevisions += commit.revision
        started.send(commit.revision)
        if (!initiallyOpen) gates.getOrPut(commit.revision) { CompletableDeferred() }.await()
        if (commit.revision in failRevisions) {
            throw PersistenceWriteException(
                TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite),
            )
        }
    }

    suspend fun awaitStarted(revision: Long) {
        while (started.receive() != revision) {
            // Ignore earlier observed revisions.
        }
    }

    fun complete(revision: Long) {
        gates.getOrPut(revision) { CompletableDeferred() }.complete(Unit)
    }
}
