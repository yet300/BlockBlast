package ge.yet.sample.counter

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
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
            CounterSessionGraph.Factory { _, _, _ ->
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

        val session = plugin.createSession(
            componentContext = lifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = RecordingMiniAppSessionHost(),
        )

        MiniAppContractAssertions.assertRetainedGraphSession(session)
        lifecycle.destroy()
    }

    @Test
    fun `session binding is scoped to one child graph`() {
        val appGraph = createGraph<CounterPluginTestGraph>()
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val host = RecordingMiniAppSessionHost()
        val sessionGraph = appGraph.sessionFactory.create(
            componentContext = lifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = host,
        )

        assertSame(sessionGraph.session, sessionGraph.session)
        val session = sessionGraph.session as CounterSession
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
        val first = appGraph.sessionFactory.create(
            componentContext = firstLifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = RecordingMiniAppSessionHost(),
        )
        val second = appGraph.sessionFactory.create(
            componentContext = secondLifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = RecordingMiniAppSessionHost(),
        )

        val firstSession = first.session as CounterSession
        val secondSession = second.session as CounterSession
        firstSession.component.onIncrementClicked()

        assertNotSame(first.session, second.session)
        assertNotSame(firstSession.component, secondSession.component)
        assertEquals(1, firstSession.component.model.value.count)
        assertEquals(0, secondSession.component.model.value.count)
        firstLifecycle.destroy()
        secondLifecycle.destroy()
    }
}
