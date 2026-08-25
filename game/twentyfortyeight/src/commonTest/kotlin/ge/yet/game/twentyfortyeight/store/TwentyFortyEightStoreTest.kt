package ge.yet.game.twentyfortyeight.store

import com.arkivanov.mvikotlin.core.rx.Disposable
import com.arkivanov.mvikotlin.core.rx.observer
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.NoopMiniAppStorage
import ge.yet.game.twentyfortyeight.diagnostics.ContractCode
import ge.yet.game.twentyfortyeight.diagnostics.StorageOperation
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import ge.yet.game.twentyfortyeight.engine.AudioControlPolicy
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.GamePhase
import ge.yet.game.twentyfortyeight.engine.GameRules
import ge.yet.game.twentyfortyeight.engine.GameState
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.engine.RngState
import ge.yet.game.twentyfortyeight.engine.RulesState
import ge.yet.game.twentyfortyeight.engine.SpawnPolicy
import ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason
import ge.yet.game.twentyfortyeight.engine.rulesState
import ge.yet.game.twentyfortyeight.engine.runtimeBoardOf
import ge.yet.game.twentyfortyeight.persistence.CheckpointResult
import ge.yet.game.twentyfortyeight.persistence.GameCommit
import ge.yet.game.twentyfortyeight.persistence.GameCommitWriter
import ge.yet.game.twentyfortyeight.persistence.GameSnapshotLoader
import ge.yet.game.twentyfortyeight.persistence.LoadResult
import ge.yet.game.twentyfortyeight.persistence.RestoredGameData
import ge.yet.game.twentyfortyeight.persistence.SessionPersistenceCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class TwentyFortyEightStoreTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bootstrap exposes no ready model until reconciliation finishes`() = runTest {
        val loader = ControlledSnapshotLoader()
        val harness = createStoreHarness(loader = loader)

        assertEquals(BootstrapState.Loading, harness.store.state.bootstrap)
        assertNull(harness.store.state.game)

        loader.complete(loaded(game = playableGame(), revision = 9L, tutorialSeen = true))
        advanceUntilIdle()

        assertEquals(BootstrapState.Ready, harness.store.state.bootstrap)
        assertEquals(9L, harness.store.state.durableRevision)
        assertTrue(harness.store.state.tutorialSeen)
        assertEquals(playableGame(), harness.store.state.game)
    }

    @Test
    fun `unfinished restore is exact and reserves resumed analytics`() = runTest {
        val restored = playableGame(score = 48L).copy(runOrdinal = 7L, bestScore = 96L)
        val harness = readyStore(loaded(game = restored, revision = 11L))

        assertEquals(restored, harness.store.state.game)
        assertEquals(11L, harness.store.state.requestedRevision)
        assertEquals(11L, harness.store.state.durableRevision)
        assertEquals(
            7L,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Analytics>()
                .mapNotNull { (it.fact as? ge.yet.game.twentyfortyeight.analytics.AnalyticsFact.GameResumed)?.runOrdinal }
                .single(),
        )
    }

    @Test
    fun `terminal restore is exact without resumed reservation`() = runTest {
        val terminal = playableGame(score = 128L).copy(phase = GamePhase.GameOver)
        val harness = readyStore(loaded(game = terminal, revision = 5L, terminal = true))

        assertEquals(terminal, harness.store.state.game)
        assertTrue(
            harness.labels.none {
                (it as? TwentyFortyEightStore.Label.Analytics)?.fact is
                    ge.yet.game.twentyfortyeight.analytics.AnalyticsFact.GameResumed
            },
        )
        assertEquals(
            terminal.score,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.NavigateToResult>()
                .single().snapshot.score,
        )
    }

    @Test
    fun `missing current game starts a fresh two tile run while retaining metadata`() = runTest {
        val statistics = GameStatistics(gamesStarted = 4L, successfulMoves = 9L, highestTileEver = 512L)
        val harness = readyStore(
            loaded(game = null, revision = 3L, bestScore = 400L, statistics = statistics),
        )

        val game = assertNotNull(harness.store.state.game)
        assertEquals(2, game.board.tiles.count { it != null })
        assertEquals(400L, game.bestScore)
        assertEquals(5L, harness.store.state.statistics.gamesStarted)
        assertEquals(9L, harness.store.state.statistics.successfulMoves)
        assertEquals(512L, harness.store.state.statistics.highestTileEver)
        assertTrue(harness.writer.commits.isNotEmpty())
    }

    @Test
    fun `load failure starts fresh and publishes typed diagnostic`() = runTest {
        val failure = TwentyFortyEightFailure.StorageRead(StorageOperation.CurrentGameRead)
        val harness = readyStore(LoadResult.Failed(failure))

        assertEquals(BootstrapState.Ready, harness.store.state.bootstrap)
        assertNotNull(harness.store.state.game)
        assertEquals(
            listOf(failure),
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>().map { it.failure },
        )
    }

    @Test
    fun `partial recovery keeps metadata and publishes every bounded validation failure`() = runTest {
        val failures: Set<TwentyFortyEightFailure> = linkedSetOf(
            TwentyFortyEightFailure.ContractViolation(ContractCode.SnapshotShape),
            TwentyFortyEightFailure.ContractViolation(ContractCode.RngAlgorithm),
        )
        val harness = readyStore(
            LoadResult.Loaded(
                data = restoredData(
                    game = null,
                    revision = 4L,
                    bestScore = 800L,
                    statistics = GameStatistics(successfulMoves = 12L, highestTileEver = 1024L),
                ),
                validationFailures = failures,
            ),
        )

        assertEquals(800L, harness.store.state.game?.bestScore)
        assertEquals(12L, harness.store.state.statistics.successfulMoves)
        assertEquals(
            failures,
            harness.labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>()
                .mapTo(linkedSetOf<TwentyFortyEightFailure>()) { it.failure },
        )
    }

    @Test
    fun `audio bootstrap starts before initial controls`() = runTest {
        val harness = readyStore(loaded(game = playableGame()))

        val audioLabels = harness.labels.filter {
            it is TwentyFortyEightStore.Label.AudioStart ||
                it is TwentyFortyEightStore.Label.AudioControlsChanged
        }
        assertIs<TwentyFortyEightStore.Label.AudioStart>(audioLabels[0])
        assertEquals(
            AudioControlPolicy.from(assertNotNull(harness.store.state.game)),
            assertIs<TwentyFortyEightStore.Label.AudioControlsChanged>(audioLabels[1]).controls,
        )
    }

    @Test
    fun `active accepts move while obscured and background reject it`() = runTest {
        val visibility = MutableVisibility(MiniAppVisibility.OBSCURED)
        val harness = readyStore(loaded(game = playableGame()), visibility = visibility)

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()
        assertNull(harness.store.state.activeTransition)

        visibility.value.value = MiniAppVisibility.BACKGROUND
        runCurrent()
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        assertNull(harness.store.state.activeTransition)

        visibility.value.value = MiniAppVisibility.ACTIVE
        runCurrent()
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        assertIs<VisualTransition.Move>(harness.store.state.activeTransition)
    }

    @Test
    fun `visual transition keeps first queued direction and runs it after matching completion`() = runTest {
        val harness = readyStore(loaded(game = playableGame()))

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        val first = assertIs<VisualTransition.Move>(harness.store.state.activeTransition)
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Up))
        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Right))

        assertEquals(Direction.Up, harness.store.state.pendingDirection)
        harness.store.accept(TwentyFortyEightStore.Intent.AnimationCompleted(first.transitionId + 99L))
        assertEquals(first, harness.store.state.activeTransition)

        harness.store.accept(TwentyFortyEightStore.Intent.AnimationCompleted(first.transitionId))
        val second = assertIs<VisualTransition.Move>(harness.store.state.activeTransition)
        assertEquals(Direction.Up, second.result.direction)
        assertTrue(second.result.direction != Direction.Right)
        assertNull(harness.store.state.pendingDirection)
    }

    @Test
    fun `statistics overlay opens and cancel clears it`() = runTest {
        val harness = readyStore(loaded(game = playableGame()))

        harness.store.accept(TwentyFortyEightStore.Intent.OpenStatistics)
        assertEquals(OverlayState.Statistics, harness.store.state.overlay)
        harness.store.accept(TwentyFortyEightStore.Intent.CancelOverlay)
        assertNull(harness.store.state.overlay)
    }

    @Test
    fun `undo without snapshot is a complete no op`() = runTest {
        val harness = readyStore(loaded(game = playableGame()))
        val before = harness.store.state
        val labelCount = harness.labels.size

        harness.store.accept(TwentyFortyEightStore.Intent.Undo)
        runCurrent()

        assertEquals(before, harness.store.state)
        assertEquals(labelCount, harness.labels.size)
    }

    @Test
    fun `score and identity move overflow publish exact diagnostic without mutation or checkpoint`() = runTest {
        val scoreOverflow = playableGame(score = Long.MAX_VALUE).copy(bestScore = Long.MAX_VALUE)
        val scoreHarness = readyStore(loaded(game = scoreOverflow))
        val scoreBefore = scoreHarness.store.state
        scoreHarness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()

        assertEquals(scoreBefore, scoreHarness.store.state)
        assertEquals(0, scoreHarness.writer.commits.size)
        assertEquals(
            TwentyFortyEightFailure.InvariantViolation(
                ge.yet.game.twentyfortyeight.diagnostics.InvariantCode.ScoreOverflow,
            ),
            scoreHarness.labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>().single().failure,
        )

        val identityOverflow = playableGame().copy(nextTileId = Long.MAX_VALUE)
        val identityHarness = readyStore(loaded(game = identityOverflow))
        val identityBefore = identityHarness.store.state
        identityHarness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()

        assertEquals(identityBefore, identityHarness.store.state)
        assertEquals(0, identityHarness.writer.commits.size)
        assertEquals(
            TwentyFortyEightFailure.InvariantViolation(
                ge.yet.game.twentyfortyeight.diagnostics.InvariantCode.IdentityOverflow,
            ),
            identityHarness.labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>().single().failure,
        )
    }

    @Test
    fun `fresh bootstrap saturates exhausted games started and reports counter overflow`() = runTest {
        val statistics = GameStatistics(gamesStarted = Long.MAX_VALUE)
        val harness = readyStore(loaded(game = null, statistics = statistics))

        assertEquals(BootstrapState.Ready, harness.store.state.bootstrap)
        assertEquals(Long.MAX_VALUE, harness.store.state.statistics.gamesStarted)
        assertEquals(Long.MAX_VALUE, harness.store.state.game?.runOrdinal)
        harness.assertSingleInvariantDiagnostic("CounterOverflow")
    }

    @Test
    fun `move and terminal counter exhaustion fail closed`() = runTest {
        val moveHarness = readyStore(
            loaded(
                game = playableGame(),
                statistics = GameStatistics(successfulMoves = Long.MAX_VALUE),
            ),
        )
        val moveBefore = moveHarness.store.state

        moveHarness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()

        assertEquals(moveBefore, moveHarness.store.state)
        assertTrue(moveHarness.writer.commits.isEmpty())
        moveHarness.assertSingleInvariantDiagnostic("CounterOverflow")

        val nearTerminal = playableGame(
            board = runtimeBoardOf(
                2L, 4L, 2L, 4L,
                4L, 2L, 4L, 2L,
                2L, 4L, 2L, 4L,
                null, 4L, 2L, 4L,
            ),
        )
        val terminalHarness = readyStore(
            loaded(
                game = nearTerminal,
                statistics = GameStatistics(gamesEndedByGameOver = Long.MAX_VALUE),
            ),
        )
        val terminalBefore = terminalHarness.store.state

        terminalHarness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()

        assertEquals(terminalBefore, terminalHarness.store.state)
        assertTrue(terminalHarness.writer.commits.isEmpty())
        terminalHarness.assertSingleInvariantDiagnostic("CounterOverflow")
    }

    @Test
    fun `undo and restart counter exhaustion fail closed`() = runTest {
        val undoHarness = readyStore(
            loaded(
                game = movedGameWithUndoForStoreTest(),
                statistics = GameStatistics(undoUses = Long.MAX_VALUE),
            ),
        )
        val undoBefore = undoHarness.store.state

        undoHarness.store.accept(TwentyFortyEightStore.Intent.Undo)

        assertEquals(undoBefore, undoHarness.store.state)
        assertTrue(undoHarness.writer.commits.isEmpty())
        undoHarness.assertSingleInvariantDiagnostic("CounterOverflow")

        val restartGame = playableGame(score = 4L).copy(successfulMovesInRun = 1L)
        val restartHarness = readyStore(
            loaded(
                game = restartGame,
                statistics = GameStatistics(gamesStarted = Long.MAX_VALUE),
            ),
        )
        restartHarness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        val restartBefore = restartHarness.store.state

        restartHarness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)

        assertEquals(restartBefore, restartHarness.store.state)
        assertTrue(restartHarness.writer.commits.isEmpty())
        restartHarness.assertSingleInvariantDiagnostic("CounterOverflow")

        val ordinalHarness = readyStore(
            loaded(
                game = restartGame.copy(runOrdinal = Long.MAX_VALUE),
                statistics = GameStatistics(gamesStarted = 1L),
            ),
        )
        ordinalHarness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        val ordinalBefore = ordinalHarness.store.state

        ordinalHarness.store.accept(TwentyFortyEightStore.Intent.ConfirmRestart)

        assertEquals(ordinalBefore, ordinalHarness.store.state)
        assertTrue(ordinalHarness.writer.commits.isEmpty())
        ordinalHarness.assertSingleInvariantDiagnostic("CounterOverflow")
    }

    @Test
    fun `unchanged move is silent and does not request persistence`() = runTest {
        val board = runtimeBoardOf(
            2L, 4L, 8L, 16L,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )
        val harness = readyStore(loaded(game = playableGame(board = board)))
        val beforeLabels = harness.labels.size
        val beforeRevision = harness.store.state.requestedRevision

        harness.store.accept(TwentyFortyEightStore.Intent.Move(Direction.Left))
        runCurrent()

        assertNull(harness.store.state.activeTransition)
        assertEquals(beforeRevision, harness.store.state.requestedRevision)
        assertEquals(beforeLabels, harness.labels.size)
    }

    @Test
    fun `undo is unavailable during transition or modal and otherwise uses typed transition`() = runTest {
        val withUndo = GameRules.acceptChanged(
            RulesState(playableGame(), GameStatistics()),
            assertIs(
                MoveEngine(SpawnPolicy()).apply(
                    ge.yet.game.twentyfortyeight.engine.MoveInput(
                        playableGame().board,
                        0L,
                        playableGame().rng,
                        playableGame().nextTileId,
                    ),
                    Direction.Left,
                    1L,
                ),
            ),
        ).game
        val harness = readyStore(loaded(game = withUndo))

        harness.store.accept(TwentyFortyEightStore.Intent.RequestRestart)
        harness.store.accept(TwentyFortyEightStore.Intent.Undo)
        assertNull(harness.store.state.activeTransition)
        harness.store.accept(TwentyFortyEightStore.Intent.CancelOverlay)

        harness.store.accept(TwentyFortyEightStore.Intent.Undo)

        assertIs<VisualTransition.Undo>(harness.store.state.activeTransition)
        assertNull(harness.store.state.game?.undo)
        assertEquals(1L, harness.store.state.statistics.undoUses)
    }

    @Test
    fun `immediate snapshot load yields before bootstrap labels are published`() {
        val dispatcher = ImmediateThenQueuedDispatcher()
        Dispatchers.setMain(dispatcher)
        val coordinator = SessionPersistenceCoordinator(
            NoopMiniAppStorage,
            ImmediateCommitWriter(),
            GameSnapshotLoader { loaded(game = playableGame()) },
        )
        val store = TwentyFortyEightStoreFactory(
            storeFactory = DefaultStoreFactory(),
            engine = MoveEngine(SpawnPolicy()),
            coordinator = coordinator,
            visibility = MutableVisibility(),
        ).create()
        val labels = mutableListOf<TwentyFortyEightStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))

        dispatcher.runQueued()

        assertEquals(BootstrapState.Ready, store.state.bootstrap)
        assertIs<TwentyFortyEightStore.Label.AudioStart>(labels.first())
        assertIs<TwentyFortyEightStore.Label.AudioControlsChanged>(labels[1])
        subscription.dispose()
        store.dispose()
    }

    @Test
    fun `factory can be constructed from only store engine coordinator and visibility`() {
        val loader = ControlledSnapshotLoader()
        val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, ImmediateCommitWriter(), loader)

        TwentyFortyEightStoreFactory(
            storeFactory = DefaultStoreFactory(),
            engine = MoveEngine(SpawnPolicy()),
            coordinator = coordinator,
            visibility = MutableVisibility(),
        )
    }

    @Test
    fun `restored terminal game ignores every stale playing intent`() = runTest {
        val terminal = terminalGameWithUndoForStoreTest()
        val harness = readyStore(
            loaded(game = terminal, terminal = true, tutorialSeen = false),
        )
        val before = harness.store.state
        val labelCount = harness.labels.size

        listOf(
            TwentyFortyEightStore.Intent.Move(Direction.Left),
            TwentyFortyEightStore.Intent.Undo,
            TwentyFortyEightStore.Intent.RequestRestart,
            TwentyFortyEightStore.Intent.ConfirmRestart,
            TwentyFortyEightStore.Intent.ContinueAfterVictory,
            TwentyFortyEightStore.Intent.OpenStatistics,
            TwentyFortyEightStore.Intent.SkipTutorial,
            TwentyFortyEightStore.Intent.CancelOverlay,
            TwentyFortyEightStore.Intent.AnimationCompleted(1L),
        ).forEach(harness.store::accept)
        advanceUntilIdle()

        assertEquals(before, harness.store.state)
        assertTrue(harness.writer.commits.isEmpty())
        assertEquals(labelCount, harness.labels.size)
        assertEquals(1, harness.labels.filterIsInstance<TwentyFortyEightStore.Label.NavigateToResult>().size)
    }
}

private class ImmediateThenQueuedDispatcher : CoroutineDispatcher() {
    private val queued = ArrayDeque<Runnable>()

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = false

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queued.addLast(block)
    }

    fun runQueued() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }
}

internal data class StoreHarness(
    val store: TwentyFortyEightStore,
    val labels: MutableList<TwentyFortyEightStore.Label>,
    val writer: StoreCommitWriter,
    val loader: ControlledSnapshotLoader,
    val coordinator: SessionPersistenceCoordinator,
    val labelSubscription: Disposable,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.createStoreHarness(
    loader: ControlledSnapshotLoader = ControlledSnapshotLoader(),
    writer: StoreCommitWriter = StoreCommitWriter(),
    visibility: MutableVisibility = MutableVisibility(),
): StoreHarness {
    val coordinator = SessionPersistenceCoordinator(NoopMiniAppStorage, writer, loader)
    val store = TwentyFortyEightStoreFactory(
        storeFactory = DefaultStoreFactory(),
        engine = MoveEngine(SpawnPolicy()),
        coordinator = coordinator,
        visibility = visibility,
    ).create()
    val labels = mutableListOf<TwentyFortyEightStore.Label>()
    val labelSubscription = store.labels(observer(onNext = labels::add))
    backgroundScope.launch {
        try {
            awaitCancellation()
        } finally {
            labelSubscription.dispose()
            store.dispose()
        }
    }
    runCurrent()
    return StoreHarness(store, labels, writer, loader, coordinator, labelSubscription)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.readyStore(
    loadResult: LoadResult,
    writer: StoreCommitWriter = StoreCommitWriter(),
    visibility: MutableVisibility = MutableVisibility(),
): StoreHarness {
    val loader = ControlledSnapshotLoader()
    val harness = createStoreHarness(loader, writer, visibility)
    loader.complete(loadResult)
    advanceUntilIdle()
    return harness
}

internal class ControlledSnapshotLoader : GameSnapshotLoader {
    private val result = CompletableDeferred<LoadResult>()

    override suspend fun load(storage: MiniAppStorage): LoadResult = result.await()

    fun complete(value: LoadResult) {
        result.complete(value)
    }
}

internal open class StoreCommitWriter(
    private val controlled: Boolean = false,
    private val failRevisions: Set<Long> = emptySet(),
) : GameCommitWriter {
    val commits = mutableListOf<GameCommit>()
    private val started = Channel<Long>(Channel.UNLIMITED)
    private val gates = mutableMapOf<Long, CompletableDeferred<Unit>>()

    override suspend fun commit(storage: MiniAppStorage, commit: GameCommit) {
        commits += commit
        started.send(commit.revision)
        if (controlled) gates.getOrPut(commit.revision) { CompletableDeferred() }.await()
        if (commit.revision in failRevisions) {
            throw ge.yet.game.twentyfortyeight.persistence.PersistenceWriteException(
                TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite),
            )
        }
    }

    suspend fun awaitStarted(revision: Long) {
        while (started.receive() != revision) {
            // Skip an earlier observed revision.
        }
    }

    fun complete(revision: Long) {
        gates.getOrPut(revision) { CompletableDeferred() }.complete(Unit)
    }
}

internal class ImmediateCommitWriter : StoreCommitWriter()

internal class MutableVisibility(
    initial: MiniAppVisibility = MiniAppVisibility.ACTIVE,
) : MiniAppVisibilitySource {
    val value = MutableStateFlow(initial)
    override val visibility = value
}

internal fun loaded(
    game: GameState?,
    revision: Long = 0L,
    bestScore: Long = game?.bestScore ?: 0L,
    statistics: GameStatistics = GameStatistics(),
    tutorialSeen: Boolean = false,
    terminal: Boolean = game?.phase == GamePhase.GameOver,
): LoadResult = LoadResult.Loaded(
    restoredData(game, revision, bestScore, statistics, tutorialSeen, terminal),
    validationFailures = emptySet(),
)

internal fun restoredData(
    game: GameState?,
    revision: Long = 0L,
    bestScore: Long = game?.bestScore ?: 0L,
    statistics: GameStatistics = GameStatistics(),
    tutorialSeen: Boolean = false,
    terminal: Boolean = game?.phase == GamePhase.GameOver,
): RestoredGameData = RestoredGameData(
    revision = revision,
    game = game,
    bestScore = bestScore,
    statistics = statistics,
    tutorialSeen = tutorialSeen,
    tutorialReason = if (tutorialSeen) TutorialCompletionReason.Skip else null,
    terminal = terminal,
)

internal fun playableGame(
    board: ge.yet.game.twentyfortyeight.engine.RuntimeBoard = runtimeBoardOf(
        2L, 2L, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
    ),
    score: Long = 0L,
): GameState = rulesState(
    board = board,
    score = score,
    bestScore = score,
    rng = RngState.fromBits(7uL),
).game

private fun movedGameWithUndoForStoreTest(): GameState {
    val initial = playableGame()
    val move = assertIs<ge.yet.game.twentyfortyeight.engine.MoveResult.Changed>(
        MoveEngine(SpawnPolicy()).apply(
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
    return GameRules.acceptChanged(RulesState(initial, GameStatistics()), move).game
}

private fun terminalGameWithUndoForStoreTest(): GameState {
    val initial = playableGame(
        board = runtimeBoardOf(
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
            2L, 4L, 2L, 4L,
            null, 4L, 2L, 4L,
        ),
    )
    val move = assertIs<ge.yet.game.twentyfortyeight.engine.MoveResult.Changed>(
        MoveEngine(SpawnPolicy()).apply(
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
    return GameRules.finishIfTerminal(
        GameRules.acceptChanged(RulesState(initial, GameStatistics()), move),
    ).game
}

private fun StoreHarness.assertSingleInvariantDiagnostic(expectedCodeName: String) {
    assertEquals(
        expectedCodeName,
        (labels.filterIsInstance<TwentyFortyEightStore.Label.Diagnostic>().single().failure as
            TwentyFortyEightFailure.InvariantViolation).code.name,
    )
}
