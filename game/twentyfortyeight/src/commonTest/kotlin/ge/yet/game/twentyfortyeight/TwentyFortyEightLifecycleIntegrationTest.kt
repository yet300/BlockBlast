package ge.yet.game.twentyfortyeight

import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.NoopMiniAppAudio
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.di.InspectableTwentyFortyEightAppGraph
import ge.yet.game.twentyfortyeight.di.InspectableTwentyFortyEightSessionGraph
import ge.yet.game.twentyfortyeight.engine.GameRules
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwentyFortyEightLifecycleIntegrationTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `visibility gates input without recreating session`() = runTest(dispatcher) {
        val app = createGraph<InspectableTwentyFortyEightAppGraph>()
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val visibility = MutableMiniAppVisibilitySource()
        val graph = app.factory.createInspectableTwentyFortyEightSessionGraph(
            TestMiniAppSessionContext(
                componentContext = lifecycle.componentContext,
                visibility = visibility,
                host = RecordingMiniAppSessionHost(),
                storage = MutableMiniAppStorage(),
                audio = NoopMiniAppAudio,
            ),
        )
        val session = graph.session
        advanceUntilIdle()
        val playing = graph.playing()
        val initial = graph.store.state
        val direction = GameRules.legalDirections(requireNotNull(initial.game).board).first()

        visibility.set(MiniAppVisibility.OBSCURED)
        runCurrent()
        playing.onMove(direction)
        advanceUntilIdle()
        assertEquals(initial.game, graph.store.state.game)

        visibility.set(MiniAppVisibility.BACKGROUND)
        runCurrent()
        visibility.set(MiniAppVisibility.ACTIVE)
        runCurrent()
        playing.onMove(direction)
        advanceUntilIdle()

        assertEquals(
            requireNotNull(initial.game).successfulMovesInRun + 1L,
            requireNotNull(graph.store.state.game).successfulMovesInRun,
        )
        assertSame(session, graph.session)
        lifecycle.destroy()
    }

    @Test
    fun `destroyed session callbacks cannot affect its successor`() = runTest(dispatcher) {
        val app = createGraph<InspectableTwentyFortyEightAppGraph>()
        val host = RecordingMiniAppSessionHost()
        val firstLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val first = app.sessionGraph(firstLifecycle, host)
        advanceUntilIdle()
        val stalePlaying = first.playing()
        firstLifecycle.destroy()

        val secondLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val second = app.sessionGraph(secondLifecycle, host)
        advanceUntilIdle()
        val secondInitial = second.store.state

        stalePlaying.onAnimationCompleted(Long.MAX_VALUE)
        stalePlaying.onStatisticsRequested()
        stalePlaying.onTutorialSkipped()
        advanceUntilIdle()

        assertEquals(secondInitial, second.store.state)
        assertEquals(0, host.closeCount)
        assertEquals(emptyList(), host.reviewRequests)
        secondLifecycle.destroy()
    }

    @Test
    fun `session Back consumes an active overlay and otherwise stays host compatible`() =
        runTest(dispatcher) {
            val app = createGraph<InspectableTwentyFortyEightAppGraph>()
            val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
            val graph = app.sessionGraph(lifecycle, RecordingMiniAppSessionHost())
            advanceUntilIdle()

            assertFalse(graph.session.handleBack())
            graph.playing().onStatisticsRequested()
            assertTrue(graph.session.handleBack())
            assertFalse(graph.session.handleBack())

            graph.concreteComponent.navigateToResult(
                ResultSnapshot(
                    score = 32L,
                    bestScore = 64L,
                    highestTile = 8L,
                    statistics = GameStatistics(),
                ),
            )
            assertFalse(graph.session.handleBack())
            lifecycle.destroy()
        }
}

private fun InspectableTwentyFortyEightSessionGraph.playing(): PlayingComponent =
    assertIs<TwentyFortyEightSessionComponent.Child.Playing>(
        component.stack.value.active.instance,
    ).component

private fun InspectableTwentyFortyEightAppGraph.sessionGraph(
    lifecycle: MiniAppLifecycleHarness,
    host: RecordingMiniAppSessionHost,
): InspectableTwentyFortyEightSessionGraph = factory.createInspectableTwentyFortyEightSessionGraph(
    TestMiniAppSessionContext(
        componentContext = lifecycle.componentContext,
        visibility = MutableMiniAppVisibilitySource(),
        host = host,
        storage = MutableMiniAppStorage(),
        audio = NoopMiniAppAudio,
    ),
).also { it.session }
