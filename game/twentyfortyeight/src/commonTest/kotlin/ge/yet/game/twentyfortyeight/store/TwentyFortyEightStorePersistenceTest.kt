package ge.yet.game.twentyfortyeight.store

import ge.yet.game.twentyfortyeight.diagnostics.StorageOperation
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.GameRules
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.engine.MoveInput
import ge.yet.game.twentyfortyeight.engine.MoveResult
import ge.yet.game.twentyfortyeight.engine.RulesState
import ge.yet.game.twentyfortyeight.engine.SpawnPolicy
import ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwentyFortyEightStorePersistenceTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tutorial skip checkpoints Skip without changing domain game state`() = runTest {
        val writer = ImmediateCommitWriter()
        val harness = readyStore(loaded(game = playableGame(), tutorialSeen = false), writer)
        val before = harness.store.state.game

        harness.store.accept(TwentyFortyEightStore.Intent.SkipTutorial)
        advanceUntilIdle()

        assertTrue(harness.store.state.tutorialSeen)
        assertEquals(before, harness.store.state.game)
        assertEquals(TutorialCompletionReason.Skip, writer.commits.single().tutorialReason)
    }

    @Test
    fun `first successful move checkpoints Move tutorial reason`() = runTest {
        val writer = ImmediateCommitWriter()
        val harness = readyStore(loaded(game = playableGame(), tutorialSeen = false), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        advanceUntilIdle()

        assertTrue(harness.store.state.tutorialSeen)
        assertEquals(TutorialCompletionReason.Move, writer.commits.single().tutorialReason)
    }

    @Test
    fun `250 ms write never delays queued move after matching animation`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val harness = readyStore(loaded(game = playableGame()), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        val firstId = assertNotNull(harness.store.state.activeTransition).transitionId
        writer.awaitStarted(1L)
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Up))
        harness.store.accept(TwentyFortyEightStore.Intent.AnimationCompleted(firstId))
        runCurrent()

        assertEquals(Direction.Up, assertIs<VisualTransition.Move>(harness.store.state.activeTransition).result.direction)
        assertEquals(2L, harness.store.state.requestedRevision)
        assertEquals(1, harness.coordinator.snapshot().inFlightCount)
        assertEquals(2L, harness.coordinator.snapshot().pendingRevision)
    }

    @Test
    fun `ordinary write failure marks dirty once without blocking play or retrying`() = runTest {
        val writer = StoreCommitWriter(failRevisions = setOf(1L))
        val harness = readyStore(loaded(game = playableGame()), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        val transitionId = assertNotNull(harness.store.state.activeTransition).transitionId
        harness.store.accept(TwentyFortyEightStore.Intent.AnimationCompleted(transitionId))
        advanceUntilIdle()

        assertNull(harness.store.state.activeTransition)
        assertTrue(harness.store.state.persistenceDirty)
        assertEquals(listOf(1L), writer.commits.map { it.revision })
        assertEquals(
            listOf(UiErrorCode.ProgressNotSaved),
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.TransientError>().map { it.code },
        )
        assertEquals(
            listOf(TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite)),
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>().map { it.failure },
        )
    }

    @Test
    fun `later meaningful checkpoint makes failed ordinary state durable and clean`() = runTest {
        val writer = StoreCommitWriter(failRevisions = setOf(1L))
        val harness = readyStore(loaded(game = playableGame()), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        harness.store.accept(
            TwentyFortyEightStore.Intent.AnimationCompleted(
                assertNotNull(harness.store.state.activeTransition).transitionId,
            ),
        )
        advanceUntilIdle()
        assertTrue(harness.store.state.persistenceDirty)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Right))
        harness.store.accept(
            TwentyFortyEightStore.Intent.AnimationCompleted(
                assertNotNull(harness.store.state.activeTransition).transitionId,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), writer.commits.map { it.revision })
        assertEquals(2L, harness.store.state.durableRevision)
        assertFalse(harness.store.state.persistenceDirty)
    }

    @Test
    fun `stale checkpoint completion cannot roll durable revision backward`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val harness = readyStore(loaded(game = playableGame(), revision = 4L), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        writer.awaitStarted(5L)
        harness.store.accept(
            TwentyFortyEightStore.Intent.AnimationCompleted(
                assertNotNull(harness.store.state.activeTransition).transitionId,
            ),
        )
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Up))
        runCurrent()
        writer.complete(5L)
        writer.awaitStarted(6L)
        writer.complete(6L)
        advanceUntilIdle()

        assertEquals(6L, harness.store.state.durableRevision)
        assertFalse(harness.store.state.persistenceDirty)
    }

    @Test
    fun `restart keeps old run visible until exact barrier succeeds`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val old = playableGame(score = 40L).copy(runOrdinal = 4L, bestScore = 80L)
        val harness = readyStore(loaded(game = old), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        runCurrent()
        writer.awaitStarted(1L)

        assertEquals(4L, harness.store.state.game?.runOrdinal)
        writer.complete(1L)
        advanceUntilIdle()

        assertEquals(5L, harness.store.state.game?.runOrdinal)
        assertNull(harness.store.state.game?.undo)
        assertNull(harness.store.state.overlay)
        assertEquals(5L, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.NewGameCommitted>().single().runOrdinal)
    }

    @Test
    fun `move and undo cannot mutate old run while destructive barrier is pending`() = runTest {
        val restartWriter = StoreCommitWriter(controlled = true)
        val restart = readyStore(loaded(game = playableGame()), restartWriter)
        restart.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        runCurrent()
        restartWriter.awaitStarted(1L)
        val restartPending = restart.store.state

        restart.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()

        assertEquals(restartPending, restart.store.state)
        assertEquals(listOf(1L), restartWriter.commits.map { it.revision })

        val beforeMove = playableGame()
        val changed = assertIs<MoveResult.Changed>(
            MoveEngine(SpawnPolicy()).apply(
                MoveInput(
                    board = beforeMove.board,
                    score = beforeMove.score,
                    rng = beforeMove.rng,
                    nextTileId = beforeMove.nextTileId,
                ),
                direction = Direction.Left,
                transitionId = 1L,
            ),
        )
        val terminalWithUndo = GameRules.acceptChanged(
            RulesState(beforeMove, GameStatistics()),
            changed,
        ).game.copy(phase = ge.yet.game.twentyfortyeight.engine.GamePhase.GameOver)
        val resultWriter = StoreCommitWriter(controlled = true)
        val result = readyStore(loaded(game = terminalWithUndo, terminal = true), resultWriter)
        result.store.accept(TwentyFortyEightStore.Intent.NewGameFromResult)
        runCurrent()
        resultWriter.awaitStarted(1L)
        val resultPending = result.store.state

        result.store.accept(TwentyFortyEightStore.Intent.Undo)
        runCurrent()

        assertEquals(resultPending, result.store.state)
        assertEquals(listOf(1L), resultWriter.commits.map { it.revision })
    }

    @Test
    fun `pristine restart commits directly while progressed restart asks confirmation`() = runTest {
        val pristineWriter = StoreCommitWriter(controlled = true)
        val pristine = readyStore(loaded(game = playableGame()), pristineWriter)

        pristine.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        runCurrent()
        pristineWriter.awaitStarted(1L)
        assertNull(pristine.store.state.overlay)

        val progressedWriter = StoreCommitWriter(controlled = true)
        val progressedGame = playableGame(score = 4L).copy(successfulMovesInRun = 1L)
        val progressed = readyStore(loaded(game = progressedGame), progressedWriter)
        progressed.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        runCurrent()

        assertEquals(OverlayState.RestartConfirmation, progressed.store.state.overlay)
        assertTrue(progressedWriter.commits.isEmpty())
    }

    @Test
    fun `restart is rejected while hidden transitioning or another modal owns the surface`() = runTest {
        val hiddenVisibility = MutableVisibility(ge.yet.game.miniapp.api.MiniAppVisibility.OBSCURED)
        val hidden = readyStore(loaded(game = playableGame(score = 4L)), visibility = hiddenVisibility)
        hidden.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        assertNull(hidden.store.state.overlay)

        val transitioning = readyStore(loaded(game = playableGame(score = 4L)))
        transitioning.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        transitioning.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        assertNull(transitioning.store.state.overlay)

        val modal = readyStore(loaded(game = playableGame(score = 4L)))
        modal.store.accept(TwentyFortyEightStore.Intent.OpenStatistics)
        modal.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        assertEquals(OverlayState.Statistics, modal.store.state.overlay)
    }

    @Test
    fun `hidden confirmation and cancel cannot mutate restart modal`() = runTest {
        val writer = StoreCommitWriter()
        val visibility = MutableVisibility()
        val progressed = playableGame(score = 4L).copy(successfulMovesInRun = 1L)
        val harness = readyStore(loaded(game = progressed), writer, visibility)
        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        assertEquals(OverlayState.RestartConfirmation, harness.store.state.overlay)

        visibility.value.value = ge.yet.game.miniapp.api.MiniAppVisibility.OBSCURED
        runCurrent()
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.CancelOverlay)

        assertEquals(OverlayState.RestartConfirmation, harness.store.state.overlay)
        assertTrue(writer.commits.isEmpty())
    }

    @Test
    fun `victory restart confirms and records victory source`() = runTest {
        val victorious = playableGame(score = 2048L).copy(
            successfulMovesInRun = 4L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = readyStore(loaded(game = victorious))
        assertEquals(OverlayState.Victory, harness.store.state.overlay)

        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        assertEquals(OverlayState.RestartConfirmation, harness.store.state.overlay)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        advanceUntilIdle()

        assertEquals(
            ge.yet.game.twentyfortyeight.analytics.RestartSource.Victory,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
                .mapNotNull { (it.fact as? ge.yet.game.twentyfortyeight.analytics.AnalyticsFact.Restart)?.source }
                .single(),
        )
    }

    @Test
    fun `victory restart cancellation restores victory and cancelling victory continues optimistically`() = runTest {
        val victorious = playableGame(score = 2048L).copy(
            successfulMovesInRun = 4L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = readyStore(loaded(game = victorious))

        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        assertEquals(OverlayState.RestartConfirmation, harness.store.state.overlay)
        harness.store.accept(TwentyFortyEightStore.Intent.CancelOverlay)
        assertEquals(OverlayState.Victory, harness.store.state.overlay)
        assertFalse(harness.store.state.game?.facts?.victoryAcknowledged == true)

        harness.store.accept(TwentyFortyEightStore.Intent.CancelOverlay)
        advanceUntilIdle()

        assertNull(harness.store.state.overlay)
        assertTrue(harness.store.state.game?.facts?.victoryAcknowledged == true)
        assertEquals(
            1,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
                .count { it.fact is ge.yet.game.twentyfortyeight.analytics.AnalyticsFact.Continued },
        )
        assertEquals(
            FocusTarget.Board,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Focus>().last().target,
        )
    }

    @Test
    fun `failed victory restart retains source for successful retry`() = runTest {
        val writer = StoreCommitWriter(failRevisions = setOf(1L))
        val victorious = playableGame(score = 2048L).copy(
            successfulMovesInRun = 4L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = readyStore(loaded(game = victorious), writer)
        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        advanceUntilIdle()

        assertEquals(OverlayState.RestartConfirmation, harness.store.state.overlay)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        advanceUntilIdle()

        assertEquals(
            listOf(ge.yet.game.twentyfortyeight.analytics.RestartSource.Victory),
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
                .mapNotNull { (it.fact as? ge.yet.game.twentyfortyeight.analytics.AnalyticsFact.Restart)?.source },
        )
    }

    @Test
    fun `failed fact checkpoint is carried through successful restart barrier exactly once`() = runTest {
        val writer = StoreCommitWriter(failRevisions = setOf(1L))
        val nearVictory = playableGame(
            board = ge.yet.game.twentyfortyeight.engine.runtimeBoardOf(
                1024L, 1024L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
        )
        val harness = readyStore(loaded(game = nearVictory), writer)
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        advanceUntilIdle()
        assertTrue(harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>().none {
            it.fact is ge.yet.game.twentyfortyeight.analytics.AnalyticsFact.Victory
        })

        harness.store.accept(
            TwentyFortyEightStore.Intent.AnimationCompleted(
                assertNotNull(harness.store.state.activeTransition).transitionId,
            ),
        )
        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        advanceUntilIdle()

        assertEquals(
            1,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
                .count { it.fact is ge.yet.game.twentyfortyeight.analytics.AnalyticsFact.Victory },
        )
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Review>().size)
    }

    @Test
    fun `revision exhaustion rejects changed input before authoritative mutation`() = runTest {
        val harness = readyStore(loaded(game = playableGame(), revision = Long.MAX_VALUE))
        val before = harness.store.state

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()

        assertEquals(before, harness.store.state)
        assertTrue(harness.writer.commits.isEmpty())
        assertEquals(
            ge.yet.game.twentyfortyeight.diagnostics.InvariantCode.RevisionRegression,
            (harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>().single().failure as
                TwentyFortyEightFailure.InvariantViolation).code,
        )
    }

    @Test
    fun `failed restart retains old run and publishes new game not saved`() = runTest {
        val writer = StoreCommitWriter(failRevisions = setOf(1L))
        val old = playableGame(score = 40L).copy(runOrdinal = 4L, bestScore = 80L)
        val harness = readyStore(loaded(game = old), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        advanceUntilIdle()

        assertEquals(old, harness.store.state.game)
        assertEquals(OverlayState.RestartConfirmation, harness.store.state.overlay)
        assertEquals(
            UiErrorCode.NewGameNotSaved,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.TransientError>().single().code,
        )
    }

    @Test
    fun `failed barrier marks dirty only when retained visible state was not durable`() = runTest {
        val writer = StoreCommitWriter(controlled = true, failRevisions = setOf(3L))
        val harness = readyStore(loaded(game = playableGame()), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        writer.awaitStarted(1L)
        harness.store.accept(
            TwentyFortyEightStore.Intent.AnimationCompleted(
                assertNotNull(harness.store.state.activeTransition).transitionId,
            ),
        )
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Down))
        runCurrent()
        harness.store.accept(
            TwentyFortyEightStore.Intent.AnimationCompleted(
                assertNotNull(harness.store.state.activeTransition).transitionId,
            ),
        )
        val retainedGame = harness.store.state.game
        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        runCurrent()

        writer.complete(1L)
        writer.awaitStarted(3L)
        writer.complete(3L)
        advanceUntilIdle()

        assertEquals(retainedGame, harness.store.state.game)
        assertEquals(1L, harness.store.state.durableRevision)
        assertEquals(3L, harness.store.state.requestedRevision)
        assertTrue(harness.store.state.persistenceDirty)

        val durableWriter = StoreCommitWriter(failRevisions = setOf(8L))
        val durable = readyStore(
            loaded(
                game = playableGame(score = 4L).copy(successfulMovesInRun = 1L),
                revision = 7L,
            ),
            durableWriter,
        )
        durable.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        durable.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        advanceUntilIdle()

        assertFalse(durable.store.state.persistenceDirty)
    }

    @Test
    fun `new game from result uses exact barrier and failure keeps terminal result`() = runTest {
        val terminal = playableGame(score = 40L).copy(
            runOrdinal = 3L,
            bestScore = 80L,
            phase = ge.yet.game.twentyfortyeight.engine.GamePhase.GameOver,
        )
        val successWriter = StoreCommitWriter(controlled = true)
        val success = readyStore(loaded(game = terminal, terminal = true), successWriter)
        success.store.accept(TwentyFortyEightStore.Intent.NewGameFromResult)
        runCurrent()
        successWriter.awaitStarted(1L)
        assertEquals(terminal, success.store.state.game)
        successWriter.complete(1L)
        advanceUntilIdle()
        assertEquals(4L, success.store.state.game?.runOrdinal)

        val failureWriter = StoreCommitWriter(failRevisions = setOf(1L))
        val failure = readyStore(loaded(game = terminal, terminal = true), failureWriter)
        failure.store.accept(TwentyFortyEightStore.Intent.NewGameFromResult)
        advanceUntilIdle()
        assertEquals(terminal, failure.store.state.game)
        assertEquals(
            UiErrorCode.NewGameNotSaved,
            failure.labels.filterIsInstance<TwentyFortyEightStore.Label.TransientError>().single().code,
        )
    }

    @Test
    fun `restart preserves cumulative statistics`() = runTest {
        val writer = StoreCommitWriter()
        val statistics = ge.yet.game.twentyfortyeight.engine.GameStatistics(
            gamesStarted = 4L,
            successfulMoves = 12L,
            totalMerges = 8L,
            highestTileEver = 512L,
        )
        val progressed = playableGame(score = 8L).copy(successfulMovesInRun = 2L)
        val harness = readyStore(loaded(game = progressed, statistics = statistics), writer)
        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)
        advanceUntilIdle()

        assertEquals(statistics.successfulMoves, harness.store.state.statistics.successfulMoves)
        assertEquals(statistics.totalMerges, harness.store.state.statistics.totalMerges)
        assertEquals(statistics.gamesStarted + 1L, harness.store.state.statistics.gamesStarted)
        assertEquals(statistics.highestTileEver, harness.store.state.statistics.highestTileEver)
    }

    @Test
    fun `continue and tutorial skip are optimistic ordinary checkpoints`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val victorious = playableGame().copy(
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = readyStore(loaded(game = victorious), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.ContinueAfterVictory)
        runCurrent()
        assertTrue(harness.store.state.game?.facts?.victoryAcknowledged == true)
        assertNull(harness.store.state.overlay)
        assertFalse(harness.store.state.tutorialSeen)

        harness.store.accept(TwentyFortyEightStore.Intent.SkipTutorial)
        runCurrent()
        assertTrue(harness.store.state.tutorialSeen)
        assertEquals(2L, harness.store.state.requestedRevision)
        assertEquals(1L, writer.commits.single().revision)
    }

    @Test
    fun `continue after Victory is rejected while RestartConfirmation is authoritative`() = runTest {
        val writer = StoreCommitWriter()
        val victorious = playableGame(score = 4L).copy(
            successfulMovesInRun = 1L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = readyStore(loaded(game = victorious), writer)
        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        val before = harness.store.state
        val labelCount = harness.labels.size
        val commitCount = writer.commits.size

        harness.store.accept(TwentyFortyEightStore.Intent.ContinueAfterVictory)
        advanceUntilIdle()

        assertEquals(OverlayState.RestartConfirmation, before.overlay)
        assertEquals(before, harness.store.state)
        assertEquals(labelCount, harness.labels.size)
        assertEquals(commitCount, writer.commits.size)
    }
}
