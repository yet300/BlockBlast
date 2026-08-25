package ge.yet.game.blockblast

import dev.zacsweers.metro.createGraph
import ge.yet.game.blockblast.di.BlockBlastPluginTestGraph
import ge.yet.game.blockblast.di.BlockBlastSessionGraph
import ge.yet.game.blockblast.di.destroySessionsAndCancelAppScope
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BlockBlastPluginContractTest {
    @Test
    fun plugin_manifest_does_not_create_a_session_graph() {
        var graphCreationCount = 0
        val plugin = BlockBlastPlugin(
            BlockBlastSessionGraph.Factory { _ ->
                graphCreationCount += 1
                error("manifest access must not create a session graph")
            },
        )

        MiniAppContractAssertions.assertManifest(plugin, MiniAppId("game.blockblast"))
        assertEquals(0, graphCreationCount)
    }

    @Test
    fun plugin_returns_a_graph_retaining_session() {
        val appGraph = createGraph<BlockBlastPluginTestGraph>()
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        try {
            val plugin = assertNotNull(appGraph.registry[MiniAppId("game.blockblast")])
            val session: MiniAppSession = plugin.createSession(TestMiniAppSessionContext(
                componentContext = lifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            ))

            MiniAppContractAssertions.assertRetainedGraphSession(session)
            MiniAppContractAssertions.assertBackNotConsumed(session)
        } finally {
            appGraph.destroySessionsAndCancelAppScope(lifecycle)
        }
    }
}
