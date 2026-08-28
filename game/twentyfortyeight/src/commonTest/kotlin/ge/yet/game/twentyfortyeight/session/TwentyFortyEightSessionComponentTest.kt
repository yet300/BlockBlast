package ge.yet.game.twentyfortyeight.session

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.miniapp.testkit.NoopMiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.twentyfortyeight.analytics.TwentyFortyEightAnalytics
import ge.yet.game.twentyfortyeight.audio.TwentyFortyEightAudioAdapter
import ge.yet.game.twentyfortyeight.component.OverlayComponent
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightDiagnostics
import ge.yet.game.twentyfortyeight.engine.GamePhase
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.engine.SpawnPolicy
import ge.yet.game.twentyfortyeight.engine.UndoSnapshot
import ge.yet.game.twentyfortyeight.persistence.GameCommitWriter
import ge.yet.game.twentyfortyeight.persistence.GameSnapshotLoader
import ge.yet.game.twentyfortyeight.persistence.LoadResult
import ge.yet.game.twentyfortyeight.persistence.RestoredGameData
import ge.yet.game.twentyfortyeight.persistence.SessionPersistenceCoordinator
import ge.yet.game.twentyfortyeight.store.AnnouncementFact
import ge.yet.game.twentyfortyeight.store.FocusTarget
import ge.yet.game.twentyfortyeight.store.NewGameSeedSource
import ge.yet.game.twentyfortyeight.store.OverlayState
import ge.yet.game.twentyfortyeight.store.StoreCommitWriter
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStoreFactory
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore.Label
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.twentyfortyeight.store.playableGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwentyFortyEightSessionComponentTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `2048 consumes Back only for active Playing overlay`() = runTest(dispatcher) {
        val harness = componentHarness(unfinishedData())
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component

        assertFalse(harness.component.handleBack())
        playing.onStatisticsRequested()
        assertIs<OverlayComponent.Model.Statistics>(playing.overlay.value.child?.instance?.model?.value)
        assertTrue(harness.component.handleBack())
        assertNull(playing.overlay.value.child)

        harness.component.navigateToResult(resultSnapshot())
        assertFalse(harness.component.handleBack())
        harness.destroy()
    }

    @Test
    fun `terminal restore creates Result once and both children use Standard frame`() = runTest(dispatcher) {
        val harness = componentHarness(terminalData())
        advanceUntilIdle()

        val first = assertIs<TwentyFortyEightSessionComponent.Child.Result>(
            harness.component.stack.value.active.instance,
        )
        assertEquals(MiniAppFrameMode.Standard, harness.component.frameMode.value)

        harness.component.navigateToResult(resultSnapshot())

        assertSame(first, harness.component.stack.value.active.instance)
        assertEquals(1, harness.component.stack.value.items.size)
        harness.destroy()
    }

    @Test
    fun `retained terminal Store recreates immediately as one Result with authoritative model`() =
        runTest(dispatcher) {
            val keeper = InstanceKeeperDispatcher()
            val statistics = GameStatistics(
                gamesStarted = 7L,
                gamesWon = 3L,
                gamesEndedByGameOver = 4L,
                successfulMoves = 80L,
                totalMerges = 42L,
                undoUses = 5L,
            )
            val terminalGame = playableGame().copy(
                score = 64L,
                bestScore = 128L,
                phase = GamePhase.GameOver,
            )
            val restored = terminalData(terminalGame, statistics)
            val first = componentHarness(restored, instanceKeeper = keeper)
            advanceUntilIdle()
            assertIs<TwentyFortyEightSessionComponent.Child.Result>(first.component.stack.value.active.instance)
            val retained = first.component.retainedStore
            first.destroy()

            val second = componentHarness(
                unfinishedData(),
                instanceKeeper = keeper,
                visibility = first.visibility,
            )
            assertSame(retained, second.component.retainedStore)
            val result = assertIs<TwentyFortyEightSessionComponent.Child.Result>(
                second.component.stack.value.active.instance,
            )
            val model = result.component.model.value
            assertEquals(64L, model.score)
            assertEquals(128L, model.bestScore)
            assertEquals(terminalGame.board.values().filterNotNull().maxOrNull(), model.highestTile)
            assertEquals(7L, model.statistics.gamesStarted)
            assertEquals(3L, model.statistics.gamesWon)
            assertEquals(4L, model.statistics.gamesEndedByGameOver)
            assertEquals(80L, model.statistics.successfulMoves)
            assertEquals(42L, model.statistics.totalMerges)
            assertEquals(5L, model.statistics.undoUses)

            advanceUntilIdle()
            assertSame(result, second.component.stack.value.active.instance)
            assertEquals(1, second.component.stack.value.items.size)
            second.destroy()
            keeper.destroy()
        }

    @Test
    fun `new game remains Result until commit then replaces with Playing`() = runTest(dispatcher) {
        val writer = StoreCommitWriter(controlled = true)
        val harness = componentHarness(terminalData(), writer)
        advanceUntilIdle()
        val result = assertIs<TwentyFortyEightSessionComponent.Child.Result>(
            harness.component.stack.value.active.instance,
        ).component

        result.onNewGameRequested()
        runCurrent()
        writer.awaitStarted(1L)
        assertIs<TwentyFortyEightSessionComponent.Child.Result>(harness.component.stack.value.active.instance)

        writer.complete(1L)
        advanceUntilIdle()
        assertIs<TwentyFortyEightSessionComponent.Child.Playing>(harness.component.stack.value.active.instance)
        harness.destroy()
    }

    @Test
    fun `Continue dismisses Victory without replacing Playing`() = runTest(dispatcher) {
        val victorious = playableGame().copy(
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = componentHarness(unfinishedData(victorious))
        advanceUntilIdle()
        val playingChild = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        )
        assertIs<OverlayComponent.Model.Victory>(playingChild.component.overlay.value.child?.instance?.model?.value)

        playingChild.component.onContinueAfterVictory()
        runCurrent()

        assertSame(playingChild, harness.component.stack.value.active.instance)
        assertNull(playingChild.component.overlay.value.child)
        harness.destroy()
    }

    @Test
    fun `Playing owns one mutually exclusive overlay and Back restores Victory`() = runTest(dispatcher) {
        val victorious = playableGame().copy(
            score = 4L,
            bestScore = 4L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = componentHarness(unfinishedData(victorious))
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component
        assertIs<OverlayComponent.Model.Victory>(playing.overlay.value.child?.instance?.model?.value)

        playing.onRestartRequested()
        assertIs<OverlayComponent.Model.RestartConfirmation>(
            playing.overlay.value.child?.instance?.model?.value,
        )
        assertEquals(1, listOfNotNull(playing.overlay.value.child).size)

        assertTrue(harness.component.handleBack())
        assertIs<OverlayComponent.Model.Victory>(playing.overlay.value.child?.instance?.model?.value)
        harness.destroy()
    }

    @Test
    fun `Back keeps restart confirmation authoritative while restart barrier is pending`() = runTest(dispatcher) {
        val writer = StoreCommitWriter(controlled = true)
        val progressed = playableGame(score = 4L).copy(successfulMovesInRun = 1L)
        val harness = componentHarness(unfinishedData(progressed), writer)
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component

        playing.onRestartRequested()
        val restart = assertIs<OverlayComponent.RestartConfirmation>(playing.overlay.value.child?.instance)
        restart.onConfirmRequested()
        runCurrent()
        writer.awaitStarted(1L)

        assertTrue(harness.component.handleBack())
        assertEquals(OverlayState.RestartConfirmation, harness.component.retainedStore.state.overlay)
        assertEquals(OverlayState.RestartConfirmation, playing.model.value.overlay)
        assertSame(restart, playing.overlay.value.child?.instance)

        writer.complete(1L)
        advanceUntilIdle()
        harness.destroy()
    }

    @Test
    fun `Back keeps an obscured active overlay coherent`() = runTest(dispatcher) {
        val harness = componentHarness(unfinishedData())
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component
        playing.onStatisticsRequested()
        val statistics = assertIs<OverlayComponent.Statistics>(playing.overlay.value.child?.instance)
        harness.visibility.set(MiniAppVisibility.OBSCURED)
        runCurrent()

        assertTrue(harness.component.handleBack())
        assertEquals(OverlayState.Statistics, harness.component.retainedStore.state.overlay)
        assertEquals(OverlayState.Statistics, playing.model.value.overlay)
        assertSame(statistics, playing.overlay.value.child?.instance)
        harness.destroy()
    }

    @Test
    fun `stale Victory callbacks cannot affect RestartConfirmation`() = runTest(dispatcher) {
        val victorious = playableGame(score = 4L).copy(
            successfulMovesInRun = 1L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = componentHarness(unfinishedData(victorious))
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component
        val victory = assertIs<OverlayComponent.Victory>(playing.overlay.value.child?.instance)
        victory.onRestartRequested()
        val restart = assertIs<OverlayComponent.RestartConfirmation>(playing.overlay.value.child?.instance)
        val before = harness.component.retainedStore.state

        victory.onContinueRequested()
        victory.onDismissRequested()

        assertEquals(before, harness.component.retainedStore.state)
        assertSame(restart, playing.overlay.value.child?.instance)
        harness.destroy()
    }

    @Test
    fun `stale RestartConfirmation callbacks cannot affect restored Victory`() = runTest(dispatcher) {
        val victorious = playableGame(score = 4L).copy(
            successfulMovesInRun = 1L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = componentHarness(unfinishedData(victorious))
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component
        assertIs<OverlayComponent.Victory>(playing.overlay.value.child?.instance).onRestartRequested()
        val restart = assertIs<OverlayComponent.RestartConfirmation>(playing.overlay.value.child?.instance)
        assertTrue(harness.component.handleBack())
        val restoredVictory = assertIs<OverlayComponent.Victory>(playing.overlay.value.child?.instance)
        val before = harness.component.retainedStore.state

        restart.onDismissRequested()
        restart.onConfirmRequested()

        assertEquals(before, harness.component.retainedStore.state)
        assertSame(restoredVictory, playing.overlay.value.child?.instance)
        harness.destroy()
    }

    @Test
    fun `stale Victory callbacks cannot affect a replacement Victory`() = runTest(dispatcher) {
        val victorious = playableGame(score = 4L).copy(
            successfulMovesInRun = 1L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val harness = componentHarness(unfinishedData(victorious))
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component
        val firstVictory = assertIs<OverlayComponent.Victory>(playing.overlay.value.child?.instance)
        firstVictory.onRestartRequested()
        assertIs<OverlayComponent.RestartConfirmation>(playing.overlay.value.child?.instance)
        assertTrue(harness.component.handleBack())
        val replacementVictory = assertIs<OverlayComponent.Victory>(playing.overlay.value.child?.instance)
        assertNotSame(firstVictory, replacementVictory)
        val before = harness.component.retainedStore.state

        firstVictory.onContinueRequested()
        firstVictory.onDismissRequested()

        assertEquals(before, harness.component.retainedStore.state)
        assertSame(replacementVictory, playing.overlay.value.child?.instance)
        harness.destroy()
    }

    @Test
    fun `stale Statistics dismissal cannot affect replacement Statistics`() = runTest(dispatcher) {
        val harness = componentHarness(unfinishedData())
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            harness.component.stack.value.active.instance,
        ).component
        playing.onStatisticsRequested()
        val firstStatistics = assertIs<OverlayComponent.Statistics>(playing.overlay.value.child?.instance)
        firstStatistics.onDismissRequested()
        playing.onStatisticsRequested()
        val replacementStatistics = assertIs<OverlayComponent.Statistics>(playing.overlay.value.child?.instance)
        assertNotSame(firstStatistics, replacementStatistics)
        val before = harness.component.retainedStore.state

        firstStatistics.onDismissRequested()

        assertEquals(before, harness.component.retainedStore.state)
        assertSame(replacementStatistics, playing.overlay.value.child?.instance)
        harness.destroy()
    }

    @Test
    fun `retained recreation reuses Store and collector cancels idempotently`() = runTest(dispatcher) {
        val keeper = InstanceKeeperDispatcher()
        val stateKeeper = StateKeeperDispatcher()
        val first = componentHarness(
            unfinishedData(),
            instanceKeeper = keeper,
            stateKeeper = stateKeeper,
        )
        advanceUntilIdle()
        val retained = first.component.retainedStore
        val saved = stateKeeper.save()

        first.destroy()
        assertTrue(first.component.labelCollector.isCancelled)
        first.destroy()

        val second = componentHarness(
            unfinishedData(),
            instanceKeeper = keeper,
            visibility = first.visibility,
            stateKeeper = StateKeeperDispatcher(saved),
        )
        assertSame(retained, second.component.retainedStore)
        assertIs<TwentyFortyEightSessionComponent.Child.Playing>(second.component.stack.value.active.instance)
        second.visibility.set(MiniAppVisibility.OBSCURED)
        runCurrent()
        assertEquals(MiniAppVisibility.OBSCURED, second.component.retainedStore.state.visibility)
        second.visibility.set(MiniAppVisibility.BACKGROUND)
        runCurrent()
        assertEquals(MiniAppVisibility.BACKGROUND, second.component.retainedStore.state.visibility)
        assertIs<TwentyFortyEightSessionComponent.Child.Playing>(second.component.stack.value.active.instance)
        assertEquals(1, second.component.stack.value.items.size)
        second.destroy()
        keeper.destroy()
    }

    @Test
    fun `StateKeeper restores Result and rebuilds its model from the authoritative Store`() = runTest(dispatcher) {
        val stateKeeper = StateKeeperDispatcher()
        val statistics = GameStatistics(
            gamesStarted = 7L,
            gamesWon = 3L,
            gamesEndedByGameOver = 4L,
            successfulMoves = 80L,
            totalMerges = 42L,
            undoUses = 5L,
        )
        val terminalGame = playableGame().copy(
            score = 64L,
            bestScore = 128L,
            phase = GamePhase.GameOver,
        )
        val first = componentHarness(
            terminalData(terminalGame, statistics),
            stateKeeper = stateKeeper,
        )
        advanceUntilIdle()
        assertIs<TwentyFortyEightSessionComponent.Child.Result>(first.component.stack.value.active.instance)
        val saved = stateKeeper.save()
        first.destroy()

        val restored = componentHarness(
            terminalData(terminalGame, statistics),
            stateKeeper = StateKeeperDispatcher(saved),
        )
        advanceUntilIdle()

        val result = assertIs<TwentyFortyEightSessionComponent.Child.Result>(
            restored.component.stack.value.active.instance,
        )
        assertEquals(1, restored.component.stack.value.items.size)
        assertEquals(64L, result.component.model.value.score)
        assertEquals(128L, result.component.model.value.bestScore)
        assertEquals(7L, result.component.model.value.statistics.gamesStarted)
        assertEquals(42L, result.component.model.value.statistics.totalMerges)
        restored.destroy()
    }

    @Test
    fun `StateKeeper restores Victory overlay`() = runTest(dispatcher) {
        val victorious = playableGame().copy(
            score = 2048L,
            bestScore = 4096L,
            facts = playableGame().facts.copy(victoryReached = true),
        )

        val restored = recreateWithStateKeeper(unfinishedData(victorious))

        val playing = restored.playing()
        val model = assertIs<OverlayComponent.Model.Victory>(playing.overlay.value.child?.instance?.model?.value)
        assertEquals(2048L, model.score)
        assertEquals(4096L, model.bestScore)
        assertEquals(OverlayState.Victory, restored.component.retainedStore.state.overlay)
        restored.destroy()
        restored.instanceKeeper.destroy()
    }

    @Test
    fun `StateKeeper restores Statistics overlay`() = runTest(dispatcher) {
        val stateKeeper = StateKeeperDispatcher()
        val first = componentHarness(unfinishedData(), stateKeeper = stateKeeper)
        advanceUntilIdle()
        first.playing().onStatisticsRequested()
        val saved = stateKeeper.save()
        val keeper = first.instanceKeeper
        first.destroy()

        val restored = componentHarness(
            unfinishedData(),
            instanceKeeper = keeper,
            visibility = first.visibility,
            stateKeeper = StateKeeperDispatcher(saved),
        )

        assertIs<OverlayComponent.Model.Statistics>(restored.playing().overlay.value.child?.instance?.model?.value)
        assertEquals(OverlayState.Statistics, restored.component.retainedStore.state.overlay)
        restored.destroy()
        keeper.destroy()
    }

    @Test
    fun `StateKeeper restores RestartConfirmation overlay`() = runTest(dispatcher) {
        val progressed = playableGame(score = 4L).copy(successfulMovesInRun = 1L)
        val stateKeeper = StateKeeperDispatcher()
        val first = componentHarness(unfinishedData(progressed), stateKeeper = stateKeeper)
        advanceUntilIdle()
        first.playing().onRestartRequested()
        val saved = stateKeeper.save()
        val keeper = first.instanceKeeper
        first.destroy()

        val restored = componentHarness(
            unfinishedData(progressed),
            instanceKeeper = keeper,
            visibility = first.visibility,
            stateKeeper = StateKeeperDispatcher(saved),
        )

        val model = assertIs<OverlayComponent.Model.RestartConfirmation>(
            restored.playing().overlay.value.child?.instance?.model?.value,
        )
        assertEquals(4L, model.score)
        assertEquals(1L, model.successfulMovesInRun)
        assertEquals(OverlayState.RestartConfirmation, restored.component.retainedStore.state.overlay)
        restored.destroy()
        keeper.destroy()
    }

    @Test
    fun `overlay callback from destroyed component is stale after StateKeeper recreation`() = runTest(dispatcher) {
        val victorious = playableGame(score = 4L).copy(
            successfulMovesInRun = 1L,
            facts = playableGame().facts.copy(victoryReached = true),
        )
        val stateKeeper = StateKeeperDispatcher()
        val keeper = InstanceKeeperDispatcher()
        val first = componentHarness(
            unfinishedData(victorious),
            instanceKeeper = keeper,
            stateKeeper = stateKeeper,
        )
        advanceUntilIdle()
        val staleVictory = assertIs<OverlayComponent.Victory>(first.playing().overlay.value.child?.instance)
        val saved = stateKeeper.save()
        first.destroy()

        val restored = componentHarness(
            unfinishedData(victorious),
            instanceKeeper = keeper,
            visibility = first.visibility,
            stateKeeper = StateKeeperDispatcher(saved),
        )
        val restoredVictory = assertIs<OverlayComponent.Victory>(restored.playing().overlay.value.child?.instance)
        val before = restored.component.retainedStore.state

        staleVictory.onContinueRequested()
        staleVictory.onDismissRequested()

        assertEquals(before, restored.component.retainedStore.state)
        assertSame(restoredVictory, restored.playing().overlay.value.child?.instance)
        restored.destroy()
        keeper.destroy()
    }

    @Test
    fun `2048 routers do not register a Decompose BackCallback`() = runTest(dispatcher) {
        val backDispatcher = BackDispatcher()
        val harness = componentHarness(unfinishedData(), backDispatcher = backDispatcher)
        advanceUntilIdle()
        harness.playing().onStatisticsRequested()

        assertFalse(backDispatcher.isEnabled)
        assertFalse(backDispatcher.back())
        assertIs<OverlayComponent.Statistics>(harness.playing().overlay.value.child?.instance)
        harness.destroy()
    }

    @Test
    fun `adapter effects are observable with unique monotonic IDs`() = runTest(dispatcher) {
        val harness = componentHarness(unfinishedData())
        advanceUntilIdle()
        harness.adapter.collect(
            flowOf(
                Label.Announcement(AnnouncementFact.Move(scoreDelta = 8L, largestMerge = 8L)),
                Label.Focus(FocusTarget.Board),
                Label.TransientError(UiErrorCode.ProgressNotSaved),
            ),
        )

        val observed = harness.component.effect.value.effects
        assertEquals(listOf(1L, 2L, 3L), observed.map { it.id })
        assertEquals(
            AnnouncementFact.Move(scoreDelta = 8L, largestMerge = 8L),
            assertIs<TwentyFortyEightSessionComponent.Effect.Announcement>(observed[0]).fact,
        )
        assertEquals(
            FocusTarget.Board,
            assertIs<TwentyFortyEightSessionComponent.Effect.Focus>(observed[1]).target,
        )
        assertEquals(
            UiErrorCode.ProgressNotSaved,
            assertIs<TwentyFortyEightSessionComponent.Effect.Error>(observed[2]).code,
        )
        harness.destroy()
    }

    @Test
    fun `effect IDs reach the positive limit once and then fail before wrapping`() {
        val allocator = EffectIdAllocator(Long.MAX_VALUE - 1L)

        assertEquals(Long.MAX_VALUE - 1L, allocator.next())
        assertEquals(Long.MAX_VALUE, allocator.next())
        assertFailsWith<IllegalStateException> { allocator.next() }
    }

    @Test
    fun `Undo model is enabled only when an undo exists and no modal is active`() = runTest(dispatcher) {
        val absent = componentHarness(unfinishedData())
        advanceUntilIdle()
        val absentPlaying = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            absent.component.stack.value.active.instance,
        ).component
        assertFalse(absentPlaying.model.value.undoEnabled)
        absent.destroy()

        val base = playableGame()
        val withUndo = base.copy(
            undo = UndoSnapshot(
                board = base.board.valueBoard(),
                score = base.score,
                rng = base.rng,
                victoryAcknowledged = base.facts.victoryAcknowledged,
                phase = base.phase,
            ),
        )
        val present = componentHarness(unfinishedData(withUndo))
        advanceUntilIdle()
        val playing = assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
            present.component.stack.value.active.instance,
        ).component
        assertTrue(playing.model.value.undoEnabled)

        playing.onStatisticsRequested()
        assertFalse(playing.model.value.undoEnabled)
        present.destroy()
    }

    @Test
    fun `UNDISPATCHED collector receives bootstrap AudioStart before controls`() = runTest(dispatcher) {
        val audio = RecordingAudio()
        val harness = componentHarness(unfinishedData(), audio = audio)

        advanceUntilIdle()

        assertEquals(listOf("start", "progress", "danger", "momentum"), audio.calls.take(4))
        harness.destroy()
    }

    private fun componentHarness(
        restored: RestoredGameData,
        writer: GameCommitWriter = GameCommitWriter { _, _ -> },
        instanceKeeper: InstanceKeeperDispatcher = InstanceKeeperDispatcher(),
        audio: RecordingAudio = RecordingAudio(),
        visibility: MutableMiniAppVisibilitySource = MutableMiniAppVisibilitySource(),
        stateKeeper: StateKeeperDispatcher = StateKeeperDispatcher(),
        backDispatcher: BackDispatcher = BackDispatcher(),
    ): Harness {
        val lifecycle = LifecycleRegistry().also(LifecycleRegistry::resume)
        val persistence = SessionPersistenceCoordinator(
            storage = NoopMiniAppStorage,
            writer = writer,
            loader = GameSnapshotLoader { LoadResult.Loaded(restored, emptySet()) },
        )
        val storeFactory = TwentyFortyEightStoreFactory(
            storeFactory = DefaultStoreFactory(),
            engine = MoveEngine(SpawnPolicy()),
            coordinator = persistence,
            visibility = visibility,
            seedSource = NewGameSeedSource { 0x2048L },
        )
        val ports = TwentyFortyEightSessionPorts()
        val adapter = TwentyFortyEightSessionAdapter(
            navigation = ports,
            audio = TwentyFortyEightAudioAdapter(audio),
            analytics = TwentyFortyEightAnalytics(NoOpAnalytics),
            diagnostics = TwentyFortyEightDiagnostics {},
            host = NoOpHost,
            uiEffects = ports,
        )
        val component = DefaultTwentyFortyEightSessionComponent(
            componentContext = DefaultComponentContext(
                lifecycle = lifecycle,
                stateKeeper = stateKeeper,
                instanceKeeper = instanceKeeper,
                backHandler = backDispatcher,
            ),
            storeFactory = storeFactory,
            adapter = adapter,
            ports = ports,
        )
        return Harness(component, adapter, lifecycle, visibility, instanceKeeper)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.recreateWithStateKeeper(
        restoredData: RestoredGameData,
    ): Harness {
        val stateKeeper = StateKeeperDispatcher()
        val keeper = InstanceKeeperDispatcher()
        val first = componentHarness(
            restoredData,
            instanceKeeper = keeper,
            stateKeeper = stateKeeper,
        )
        advanceUntilIdle()
        val saved = stateKeeper.save()
        first.destroy()
        return componentHarness(
            restoredData,
            instanceKeeper = keeper,
            visibility = first.visibility,
            stateKeeper = StateKeeperDispatcher(saved),
        )
    }

    private data class Harness(
        val component: DefaultTwentyFortyEightSessionComponent,
        val adapter: TwentyFortyEightSessionAdapter,
        val lifecycle: LifecycleRegistry,
        val visibility: MutableMiniAppVisibilitySource,
        val instanceKeeper: InstanceKeeperDispatcher,
    ) {
        fun playing(): ge.yet.game.twentyfortyeight.component.PlayingComponent =
            assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
                component.stack.value.active.instance,
            ).component

        fun destroy() {
            lifecycle.destroy()
        }
    }

    private class RecordingAudio : MiniAppAudio {
        val calls = mutableListOf<String>()
        override fun playMusic(program: AudioProgram) = AudioCommandResult.Accepted.also { calls += "start" }
        override fun stopMusic(fadeOut: AudioDuration) = AudioCommandResult.Accepted
        override fun playSfx(program: AudioProgram, name: SfxName) =
            AudioCommandResult.Accepted.also { calls += "sfx:${name.value}" }
        override fun setControl(name: AudioControlName, value: Float) =
            AudioCommandResult.Accepted.also { calls += name.value }
    }

    private data object NoOpAnalytics : AnalyticRepository {
        override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
        override fun deleteData() = Unit
    }

    private data object NoOpHost : MiniAppSessionHost {
        override fun close() = Unit
        override fun requestReview(opportunity: MiniAppReviewOpportunity) = Unit
    }
}

private fun unfinishedData(game: ge.yet.game.twentyfortyeight.engine.GameState = playableGame()) =
    RestoredGameData(0L, game, game.bestScore, GameStatistics(), true, ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason.Move, false)

private fun terminalData(
    game: ge.yet.game.twentyfortyeight.engine.GameState = playableGame().copy(phase = GamePhase.GameOver),
    statistics: GameStatistics = GameStatistics(),
): RestoredGameData = RestoredGameData(
    0L,
    game,
    game.bestScore,
    statistics,
    true,
    ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason.Move,
    true,
)

private fun resultSnapshot() = ResultSnapshot(32L, 64L, 8L, GameStatistics())
