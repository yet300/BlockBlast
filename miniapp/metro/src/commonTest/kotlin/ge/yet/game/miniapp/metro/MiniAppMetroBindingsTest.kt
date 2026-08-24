package ge.yet.game.miniapp.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [MiniAppMetroBindings::class],
)
internal interface EmptyMiniAppGraph {
    val registry: MiniAppRegistry
    val plugins: Set<MiniAppPlugin>
}

class MiniAppMetroBindingsTest {

    @Test
    fun empty_multibinding_creates_empty_registry() {
        val graph = createGraph<EmptyMiniAppGraph>()

        assertTrue(graph.plugins.isEmpty())
        assertTrue(graph.registry.manifests.isEmpty())
        assertTrue(graph.registry === graph.registry)
    }
}
