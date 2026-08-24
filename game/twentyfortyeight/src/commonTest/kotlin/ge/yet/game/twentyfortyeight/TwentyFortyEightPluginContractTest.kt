package ge.yet.game.twentyfortyeight

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.audio.presets.PlacementClick
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import kotlin.test.Test
import kotlin.test.assertNotNull

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [MiniAppMetroBindings::class],
)
interface TwentyFortyEightPluginTestGraph {
    val registry: MiniAppRegistry
}

class TwentyFortyEightPluginContractTest {
    @Test
    fun `isolated graph contains exactly this plugin`() {
        val expectedId = MiniAppId("game.twentyfortyeight")
        val graph = createGraph<TwentyFortyEightPluginTestGraph>()

        MiniAppContractAssertions.assertSinglePlugin(graph.registry, expectedId)
        val plugin = assertNotNull(graph.registry[expectedId])
        MiniAppContractAssertions.assertManifest(plugin, expectedId)
    }

    @Test
    fun `plugin creates a graph retained session`() {
        val expectedId = MiniAppId("game.twentyfortyeight")
        val graph = createGraph<TwentyFortyEightPluginTestGraph>()
        val plugin = assertNotNull(graph.registry[expectedId])
        val lifecycle = MiniAppLifecycleHarness()
        lifecycle.resume()

        val context = TestMiniAppSessionContext(
            componentContext = lifecycle.componentContext,
            visibility = MutableMiniAppVisibilitySource(),
            host = RecordingMiniAppSessionHost(),
        )
        val sharedSfx = PlacementClick()
        assertNotNull(context.audio)
        assertNotNull(sharedSfx)
        val session = plugin.createSession(context)

        MiniAppContractAssertions.assertRetainedGraphSession(session)
        lifecycle.destroy()
    }
}
