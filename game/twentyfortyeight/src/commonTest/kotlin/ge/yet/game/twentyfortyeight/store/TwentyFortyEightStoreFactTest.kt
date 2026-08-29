package ge.yet.game.twentyfortyeight.store

import ge.yet.game.twentyfortyeight.analytics.AnalyticsBucketPolicy
import ge.yet.game.twentyfortyeight.analytics.AnalyticsFact
import ge.yet.game.twentyfortyeight.audio.AudioEvent
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.GameStatistics
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
class TwentyFortyEightStoreFactTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `move publishes typed audio and controls only when tuple changes`() = runTest {
        val harness = readyStore(loaded(game = playableGame()))
        val bootstrapControls = harness.labels.filterIsInstance<TwentyFortyEightStore.Label.AudioControlsChanged>().size

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        advanceUntilIdle()

        val resolved = assertIs<AudioEvent.MoveResolved>(
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Audio>()
                .map { it.event }
                .filterIsInstance<AudioEvent.MoveResolved>()
                .single(),
        )
        assertTrue(resolved.spawned)
        assertEquals(listOf(4L), resolved.mergeValues.map { it.value })
        val controlLabels = harness.labels.filterIsInstance<TwentyFortyEightStore.Label.AudioControlsChanged>()
        assertEquals(bootstrapControls + 1, controlLabels.size)
        assertEquals(
            ge.yet.game.twentyfortyeight.engine.AudioControlPolicy.from(assertNotNull(harness.store.state.game)),
            controlLabels.last().controls,
        )
    }

    @Test
    fun `meaningful state change with equivalent quantized controls emits no redundant control label`() = runTest {
        val victoryState = playableGame().copy(
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = readyStore(loaded(game = victoryState))
        val before = harness.labels.filterIsInstance<TwentyFortyEightStore.Label.AudioControlsChanged>().size

        harness.store.accept(TwentyFortyEightStore.Intent.ContinueAfterVictory)
        advanceUntilIdle()

        assertTrue(harness.store.state.game?.facts?.victoryAcknowledged == true)
        assertEquals(before, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.AudioControlsChanged>().size)
    }

    @Test
    fun `new best milestone and victory facts wait for carrying checkpoint durability`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
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
        runCurrent()
        writer.awaitStarted(1L)

        assertTrue(harness.labels.none { it is TwentyFortyEightStore.Label.Review })
        assertTrue(harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>().none {
            it.fact is AnalyticsFact.NewBest || it.fact is AnalyticsFact.Victory
        })

        writer.complete(1L)
        advanceUntilIdle()

        assertEquals(
            AnnouncementFact.Move(scoreDelta = 2048L, largestMerge = 2048L),
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Announcement>()
                .map { it.message }
                .filterIsInstance<AnnouncementFact.Move>()
                .single(),
        )
        assertEquals(
            AnnouncementFact.NewBest(2048L),
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Announcement>()
                .map { it.message }
                .filterIsInstance<AnnouncementFact.NewBest>()
                .single(),
        )
        assertEquals(
            1,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Announcement>()
                .count { it.message == AnnouncementFact.Victory },
        )
        assertEquals(
            1,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Focus>()
                .count { it.target == FocusTarget.Victory },
        )
        val facts = harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>().map { it.fact }
        assertEquals(1, facts.filterIsInstance<AnalyticsFact.NewBest>().size)
        assertEquals(AnalyticsBucketPolicy.score(2048L), facts.filterIsInstance<AnalyticsFact.NewBest>().single().scoreBucket)
        assertEquals(1, facts.filterIsInstance<AnalyticsFact.MilestoneReached>().count { it.value == 2048L })
        assertEquals(1, facts.filterIsInstance<AnalyticsFact.Victory>().size)
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Review>().size)
    }

    @Test
    fun `coalesced checkpoint carries reserved facts and publishes them once`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val harness = readyStore(loaded(game = playableGame()), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        writer.awaitStarted(1L)
        val firstTransition = assertNotNull(harness.store.state.activeTransition)
        harness.store.accept(TwentyFortyEightStore.Intent.AnimationCompleted(firstTransition.transitionId))
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Up))
        runCurrent()

        writer.complete(1L)
        writer.awaitStarted(2L)
        writer.complete(2L)
        advanceUntilIdle()

        val newBest = harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
            .count { it.fact is AnalyticsFact.NewBest }
        assertEquals(1, newBest)
    }

    @Test
    fun `undo increments only undo use cumulative statistic and emits typed facts`() = runTest {
        val initialStatistics = GameStatistics(successfulMoves = 5L, totalMerges = 3L, totalScoreEarned = 20L)
        val initial = ge.yet.game.twentyfortyeight.engine.RulesState(playableGame(), initialStatistics)
        val moved = ge.yet.game.twentyfortyeight.engine.GameRules.acceptChanged(
            initial,
            assertIs(
                ge.yet.game.twentyfortyeight.engine.MoveEngine(ge.yet.game.twentyfortyeight.engine.SpawnPolicy()).apply(
                    ge.yet.game.twentyfortyeight.engine.MoveInput(
                        initial.game.board,
                        initial.game.score,
                        initial.game.rng,
                        initial.game.nextTileId,
                    ),
                    Direction.Left,
                    1L,
                ),
            ),
        )
        val harness = readyStore(loaded(game = moved.game, statistics = moved.statistics))

        val before = harness.store.state.statistics
        harness.store.accept(TwentyFortyEightStore.Intent.Undo)
        advanceUntilIdle()

        assertEquals(before.copy(undoUses = before.undoUses + 1L), harness.store.state.statistics)
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
            .count { it.fact is AnalyticsFact.UndoUsed })
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Audio>()
            .count { it.event == AudioEvent.Undo })
    }

    @Test
    fun `identical undo facts remain distinct across a replaced checkpoint`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val harness = readyStore(
            loaded(game = movedGameWithUndo(), tutorialSeen = true),
            writer,
        )

        harness.store.accept(TwentyFortyEightStore.Intent.Undo)
        runCurrent()
        writer.awaitStarted(1L)
        harness.completeCurrentAnimation()
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        harness.completeCurrentAnimation()
        harness.store.accept(TwentyFortyEightStore.Intent.Undo)
        runCurrent()

        assertEquals(0, harness.undoFactCount())
        writer.complete(1L)
        writer.awaitStarted(3L)
        assertEquals(1, harness.undoFactCount())
        writer.complete(3L)
        advanceUntilIdle()

        assertEquals(2, harness.undoFactCount())
    }

    @Test
    fun `stalled writer retains only newest sixty four external fact occurrences`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val harness = readyStore(
            loaded(game = movedGameWithUndo(), tutorialSeen = true),
            writer,
        )

        repeat(65) { cycle ->
            harness.store.accept(TwentyFortyEightStore.Intent.Undo)
            runCurrent()
            if (cycle == 0) writer.awaitStarted(1L)
            harness.completeCurrentAnimation()
            harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
            runCurrent()
            harness.completeCurrentAnimation()
        }

        assertEquals(0, harness.undoFactCount())
        assertEquals(
            1,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>().count {
                (it.failure as? ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure.InvariantViolation)
                    ?.code?.name == "PendingFactOverflow"
            },
        )
        writer.complete(1L)
        writer.awaitStarted(130L)
        writer.complete(130L)
        advanceUntilIdle()

        assertEquals(64, harness.undoFactCount())
    }

    @Test
    fun `game over navigation is immediate while checkpoint remains in flight`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val nearTerminal = playableGame(
            board = ge.yet.game.twentyfortyeight.engine.runtimeBoardOf(
                2L, 4L, 2L, 4L,
                4L, 2L, 4L, 2L,
                2L, 4L, 2L, 4L,
                null, 4L, 2L, 4L,
            ),
        )
        val harness = readyStore(loaded(game = nearTerminal), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        writer.awaitStarted(1L)

        assertNotNull(harness.labels.filterIsInstance<TwentyFortyEightStore.Label.NavigateToResult>().single())
        assertFalse(writer.commits.isEmpty())
        assertEquals(0L, harness.store.state.durableRevision)
    }

    @Test
    fun `terminal move clears visual gate so result can start exact new game barrier`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val nearTerminal = playableGame(
            board = ge.yet.game.twentyfortyeight.engine.runtimeBoardOf(
                2L, 4L, 2L, 4L,
                4L, 2L, 4L, 2L,
                2L, 4L, 2L, 4L,
                null, 4L, 2L, 4L,
            ),
        )
        val harness = readyStore(loaded(game = nearTerminal), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        writer.awaitStarted(1L)

        assertEquals(ge.yet.game.twentyfortyeight.engine.GamePhase.GameOver, harness.store.state.game?.phase)
        assertNull(harness.store.state.activeTransition)
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.NavigateToResult>().size)
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Announcement>()
            .count { it.message == AnnouncementFact.GameOver })
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Focus>()
            .count { it.target == FocusTarget.Result })

        harness.store.accept(TwentyFortyEightStore.Intent.NewGameFromResult)
        runCurrent()
        writer.complete(1L)
        writer.awaitStarted(2L)
        writer.complete(2L)
        advanceUntilIdle()

        assertEquals(2L, harness.store.state.game?.runOrdinal)
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Audio>()
            .count { it.event == AudioEvent.TileSpawn })
    }

    @Test
    fun `terminal move rejects stale playing intents before result navigation is consumed`() = runTest {
        val writer = StoreCommitWriter(controlled = true)
        val nearTerminal = playableGame(
            board = ge.yet.game.twentyfortyeight.engine.runtimeBoardOf(
                2L, 4L, 2L, 4L,
                4L, 2L, 4L, 2L,
                2L, 4L, 2L, 4L,
                null, 4L, 2L, 4L,
            ),
        )
        val harness = readyStore(loaded(game = nearTerminal), writer)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        writer.awaitStarted(1L)
        val labelCount = harness.labels.size

        harness.store.accept(TwentyFortyEightStore.Intent.Undo)
        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        runCurrent()

        assertEquals(ge.yet.game.twentyfortyeight.engine.GamePhase.GameOver, harness.store.state.game?.phase)
        assertEquals(1L, harness.store.state.requestedRevision)
        assertNull(harness.store.state.activeTransition)
        assertNull(harness.store.state.overlay)
        assertEquals(listOf(1L), writer.commits.map { it.revision })
        assertEquals(labelCount, harness.labels.size)
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.NavigateToResult>().size)
    }
}

private fun movedGameWithUndo(): ge.yet.game.twentyfortyeight.engine.GameState {
    val initial = playableGame()
    val move = assertIs<ge.yet.game.twentyfortyeight.engine.MoveResult.Changed>(
        ge.yet.game.twentyfortyeight.engine.MoveEngine(ge.yet.game.twentyfortyeight.engine.SpawnPolicy()).apply(
            ge.yet.game.twentyfortyeight.engine.MoveInput(
                initial.board,
                initial.score,
                initial.rng,
                initial.nextTileId,
            ),
            Direction.Left,
            1L,
        ),
    )
    return ge.yet.game.twentyfortyeight.engine.GameRules.acceptChanged(
        ge.yet.game.twentyfortyeight.engine.RulesState(initial, GameStatistics()),
        move,
    ).game
}

private fun StoreHarness.completeCurrentAnimation() {
    store.accept(
        TwentyFortyEightStore.Intent.AnimationCompleted(
            assertNotNull(store.state.activeTransition).transitionId,
        ),
    )
}

private fun StoreHarness.undoFactCount(): Int =
    labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
        .count { it.fact is AnalyticsFact.UndoUsed }
