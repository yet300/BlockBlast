package ge.yet.sample.counter

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [MiniAppMetroBindings::class],
)
interface CounterPluginTestGraph {
    val registry: MiniAppRegistry
    val sessionFactory: CounterSessionGraph.Factory
}

class CounterPluginContractTest {
    @Test
    fun `isolated final graph contains exactly Counter`() {
        val graph = createGraph<CounterPluginTestGraph>()
        val expectedId = MiniAppId("sample.counter")

        MiniAppContractAssertions.assertSinglePlugin(graph.registry, expectedId)
    }

    @Test
    fun `manifest is available without creating a session graph`() {
        val plugin = CounterPlugin(
            CounterSessionGraph.Factory { _ ->
                error("manifest access must not create a session graph")
            },
        )

        MiniAppContractAssertions.assertManifest(plugin, MiniAppId("sample.counter"))
    }

    @Test
    fun `plugin accepts runtime host inputs and retains its child graph`() {
        val graph = createGraph<CounterPluginTestGraph>()
        val plugin = assertNotNull(graph.registry[MiniAppId("sample.counter")])
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }

        val session = plugin.createSession(TestMiniAppSessionContext(
            componentContext = lifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = RecordingMiniAppSessionHost(),
        ))

        MiniAppContractAssertions.assertRetainedGraphSession(session)
        MiniAppContractAssertions.assertBackNotConsumed(session)
        lifecycle.destroy()
    }

    @Test
    fun `session binding is scoped to one child graph`() {
        val appGraph = createGraph<CounterPluginTestGraph>()
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val host = RecordingMiniAppSessionHost()
        val sessionGraph = appGraph.sessionFactory.createSampleCounterSessionGraph(TestMiniAppSessionContext(
            componentContext = lifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = host,
        ))

        assertSame(sessionGraph.session, sessionGraph.session)
        val session = sessionGraph.session
        assertSame(host, session.host)
        lifecycle.destroy()
        lifecycle.destroy()
        assertEquals(
            1,
            (session.component as DefaultCounterComponent).destroyCount,
        )
    }

    @Test
    fun `two child graphs isolate component state and session identity`() {
        val appGraph = createGraph<CounterPluginTestGraph>()
        val firstLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val secondLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val firstAudio = RecordingMiniAppAudio()
        val secondAudio = RecordingMiniAppAudio()
        val first = appGraph.sessionFactory.createSampleCounterSessionGraph(TestMiniAppSessionContext(
            componentContext = firstLifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = RecordingMiniAppSessionHost(),
            audio = firstAudio,
        ))
        val second = appGraph.sessionFactory.createSampleCounterSessionGraph(TestMiniAppSessionContext(
            componentContext = secondLifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = RecordingMiniAppSessionHost(),
            audio = secondAudio,
        ))

        val firstSession = first.session
        val secondSession = second.session
        firstSession.component.onIncrementClicked()

        assertNotSame(first.session, second.session)
        assertNotSame(firstSession.component, secondSession.component)
        assertEquals(1, firstSession.component.model.value.count)
        assertEquals(0, secondSession.component.model.value.count)
        assertEquals(listOf(SfxName("placement_click")), firstAudio.sfxNames)
        assertEquals(emptyList(), secondAudio.sfxNames)
        firstLifecycle.destroy()
        secondLifecycle.destroy()
    }

    private class RecordingMiniAppAudio : MiniAppAudio {
        val sfxNames = mutableListOf<SfxName>()

        override fun playMusic(program: AudioProgram): AudioCommandResult = AudioCommandResult.Accepted
        override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult = AudioCommandResult.Accepted
        override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult =
            AudioCommandResult.Accepted.also { sfxNames += name }
        override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
            AudioCommandResult.Accepted
    }
}
