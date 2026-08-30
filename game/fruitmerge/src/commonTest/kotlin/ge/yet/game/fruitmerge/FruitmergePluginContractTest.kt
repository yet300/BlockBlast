package ge.yet.game.fruitmerge

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.miniapp_description
import ge.yet.game.fruitmerge.generated.resources.miniapp_icon
import ge.yet.game.fruitmerge.generated.resources.miniapp_title
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppInterstitialGate
import ge.yet.game.miniapp.compose.MiniAppInterstitialPlacement
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        MiniAppMetroBindings::class,
        FruitMergeGraphTestBindings::class,
    ],
)
internal interface FruitMergePluginTestGraph {
    val registry: MiniAppRegistry
}

@ContributesTo(AppScope::class)
@BindingContainer
internal object FruitMergeGraphTestBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideInterstitials(): MiniAppInterstitialCapability = NoOpInterstitialCapability
}

class FruitMergePluginContractTest {
    @Test
    fun `manifest access does not create a child graph`() {
        var graphCreations = 0
        val plugin = FruitMergePlugin(
            FruitMergeSessionGraph.Factory {
                graphCreations += 1
                error("Session graph must not be created while reading metadata")
            },
        )

        assertEquals(MiniAppId("game.fruitmerge"), plugin.manifest.id)
        assertEquals(Res.string.miniapp_title, plugin.manifest.title)
        assertEquals(0, graphCreations)
    }

    @Test
    fun `manifest exposes catalog resources and ordering`() {
        val plugin = assertNotNull(
            createGraph<FruitMergePluginTestGraph>().registry[MiniAppId("game.fruitmerge")],
        )

        assertEquals(MiniAppId("game.fruitmerge"), plugin.manifest.id)
        assertEquals(Res.string.miniapp_title, plugin.manifest.title)
        assertEquals(Res.string.miniapp_description, plugin.manifest.description)
        assertEquals(Res.drawable.miniapp_icon, plugin.manifest.icon)
        assertNull(plugin.manifest.cover)
        assertEquals(MiniAppCategoryId("game"), plugin.manifest.category)
        assertEquals(0, plugin.manifest.sortPriority)
    }

    @Test
    fun `isolated graph contains exactly this plugin`() {
        val expectedId = MiniAppId("game.fruitmerge")
        val graph = createGraph<FruitMergePluginTestGraph>()

        MiniAppContractAssertions.assertSinglePlugin(graph.registry, expectedId)
        val plugin = assertNotNull(graph.registry[expectedId])
        MiniAppContractAssertions.assertManifest(plugin, expectedId)
    }

    @Test
    fun `plugin creates a graph retained session`() {
        val graph = createGraph<FruitMergePluginTestGraph>()
        val plugin = assertNotNull(graph.registry[MiniAppId("game.fruitmerge")])
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val session = plugin.createSession(
            TestMiniAppSessionContext(
                componentContext = lifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            ),
        )

        MiniAppContractAssertions.assertRetainedGraphSession(session)
        MiniAppContractAssertions.assertBackNotConsumed(session)
        lifecycle.destroy()
    }

    @Test
    fun `destroying a retained plugin lifecycle repeatedly is idempotent`() {
        val graph = createGraph<FruitMergePluginTestGraph>()
        val plugin = assertNotNull(graph.registry[MiniAppId("game.fruitmerge")])
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val session = plugin.createSession(
            TestMiniAppSessionContext(
                componentContext = lifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            ),
        )

        MiniAppContractAssertions.assertRetainedGraphSession(session)
        lifecycle.destroy()
        lifecycle.destroy()
    }
}

private data object NoOpInterstitialCapability : MiniAppInterstitialCapability {
    @Composable
    override fun rememberGate(placement: MiniAppInterstitialPlacement): MiniAppInterstitialGate =
        MiniAppInterstitialGate(willShowAd = false) { onComplete -> onComplete() }
}
