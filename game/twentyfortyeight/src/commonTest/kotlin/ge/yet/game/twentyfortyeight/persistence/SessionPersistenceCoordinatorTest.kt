package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.testkit.NoopMiniAppStorage
import ge.yet.game.twentyfortyeight.diagnostics.InvariantCode
import ge.yet.game.twentyfortyeight.diagnostics.StorageOperation
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import kotlinx.coroutines.CancellationException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun `accepted revisions remain monotonic while a write is in flight`() = runTest {
        val writer = ControlledCommitWriter()
        val results = mutableListOf<CheckpointResult>()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)

        backgroundScope.launch { coordinator.submit(gameCommit(5L), results::add) }
        writer.awaitStarted(5L)
        coordinator.submit(gameCommit(7L), results::add)
        coordinator.submit(gameCommit(6L), results::add)
        runCurrent()

        assertEquals(
            CheckpointResult.Failed(
                revision = 6L,
                failure = TwentyFortyEightFailure.InvariantViolation(InvariantCode.RevisionRegression),
            ),
            results.single(),
        )
        assertEquals(7L, coordinator.snapshot().pendingRevision)

        writer.complete(5L)
        writer.awaitStarted(7L)
        writer.complete(7L)
        runCurrent()

        assertEquals(listOf(5L, 7L), writer.startedRevisions)
    }

    @Test
    fun `revision below durable revision is rejected without a write`() = runTest {
        val writer = ControlledCommitWriter(initiallyOpen = true)
        val results = mutableListOf<CheckpointResult>()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)

        coordinator.submit(gameCommit(7L), results::add)
        coordinator.submit(gameCommit(6L), results::add)

        assertEquals(listOf(7L), writer.startedRevisions)
        assertEquals(CheckpointResult.Stored(7L), results.first())
        assertEquals(
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.RevisionRegression),
            assertIs<CheckpointResult.Failed>(results.last()).failure,
        )
    }

    @Test
    fun `cancelling drainer cancels pending barrier instead of leaving it suspended`() = runTest {
        val writer = ControlledCommitWriter()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)
        val active = launch { coordinator.submit(gameCommit(5L), onResult = {}) }
        writer.awaitStarted(5L)
        val barrier = async { coordinator.commitBeforeVisible(gameCommit(7L)) }
        runCurrent()

        active.cancelAndJoin()

        assertTrue(barrier.isCompleted)
        assertFailsWith<CancellationException> { barrier.await() }
        assertEquals(CoordinatorSnapshot(null, null), coordinator.snapshot())
        assertEquals(listOf(5L), writer.startedRevisions)
    }

    @Test
    fun `second barrier is rejected without replacing the first`() = runTest {
        val writer = ControlledCommitWriter()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)
        backgroundScope.launch { coordinator.submit(gameCommit(5L), onResult = {}) }
        writer.awaitStarted(5L)
        val first = async { coordinator.commitBeforeVisible(gameCommit(7L)) }
        runCurrent()
        val second = async { coordinator.commitBeforeVisible(gameCommit(8L)) }
        runCurrent()

        assertEquals(
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.BarrierPending),
            assertIs<CheckpointResult.Failed>(second.await()).failure,
        )
        assertFalse(first.isCompleted)
        assertEquals(7L, coordinator.snapshot().pendingRevision)

        writer.complete(5L)
        writer.awaitStarted(7L)
        writer.complete(7L)

        assertEquals(CheckpointResult.Stored(7L), first.await())
        assertEquals(listOf(5L, 7L), writer.startedRevisions)
    }

    @Test
    fun `ordinary submit during pending barrier receives a defined rejection`() = runTest {
        val writer = ControlledCommitWriter()
        val results = mutableListOf<CheckpointResult>()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer)
        backgroundScope.launch { coordinator.submit(gameCommit(5L), onResult = {}) }
        writer.awaitStarted(5L)
        val barrier = async { coordinator.commitBeforeVisible(gameCommit(7L)) }
        runCurrent()

        coordinator.submit(gameCommit(8L), results::add)

        assertEquals(
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.BarrierPending),
            assertIs<CheckpointResult.Failed>(results.single()).failure,
        )
        assertEquals(7L, coordinator.snapshot().pendingRevision)

        writer.complete(5L)
        writer.awaitStarted(7L)
        writer.complete(7L)

        assertEquals(CheckpointResult.Stored(7L), barrier.await())
        assertEquals(listOf(5L, 7L), writer.startedRevisions)
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
