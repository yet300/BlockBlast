package ge.yet.game.twentyfortyeight.session

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
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
import ge.yet.game.twentyfortyeight.persistence.GameCommitWriter
import ge.yet.game.twentyfortyeight.persistence.GameSnapshotLoader
import ge.yet.game.twentyfortyeight.persistence.LoadResult
import ge.yet.game.twentyfortyeight.persistence.RestoredGameData
import ge.yet.game.twentyfortyeight.persistence.SessionPersistenceCoordinator
import ge.yet.game.twentyfortyeight.store.NewGameSeedSource
import ge.yet.game.twentyfortyeight.store.StoreCommitWriter
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStoreFactory
import ge.yet.game.twentyfortyeight.store.playableGame
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
    fun `retained recreation reuses Store and collector cancels idempotently`() = runTest(dispatcher) {
        val keeper = InstanceKeeperDispatcher()
        val first = componentHarness(unfinishedData(), instanceKeeper = keeper)
        advanceUntilIdle()
        val retained = first.component.retainedStore

        first.destroy()
        assertTrue(first.component.labelCollector.isCancelled)
        first.destroy()

        val second = componentHarness(
            unfinishedData(),
            instanceKeeper = keeper,
            visibility = first.visibility,
        )
        assertSame(retained, second.component.retainedStore)
        second.visibility.set(MiniAppVisibility.OBSCURED)
        runCurrent()
        assertEquals(MiniAppVisibility.OBSCURED, second.component.retainedStore.state.visibility)
        second.visibility.set(MiniAppVisibility.BACKGROUND)
        runCurrent()
        assertEquals(MiniAppVisibility.BACKGROUND, second.component.retainedStore.state.visibility)
        second.destroy()
        keeper.destroy()
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
            componentContext = DefaultComponentContext(lifecycle = lifecycle, instanceKeeper = instanceKeeper),
            storeFactory = storeFactory,
            adapter = adapter,
            ports = ports,
        )
        return Harness(component, lifecycle, visibility)
    }

    private data class Harness(
        val component: DefaultTwentyFortyEightSessionComponent,
        val lifecycle: LifecycleRegistry,
        val visibility: MutableMiniAppVisibilitySource,
    ) {
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

private fun terminalData(): RestoredGameData {
    val game = playableGame().copy(phase = GamePhase.GameOver)
    return RestoredGameData(0L, game, game.bestScore, GameStatistics(), true, ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason.Move, true)
}

private fun resultSnapshot() = ResultSnapshot(32L, 64L, 8L, GameStatistics())
