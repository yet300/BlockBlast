package ge.yet.game.twentyfortyeight

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.audio.presets.PlacementClick
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import ge.yet.game.twentyfortyeight.di.TwentyFortyEightAppBindings
import kotlin.test.Test
import kotlin.test.assertNotNull

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        MiniAppMetroBindings::class,
        TwentyFortyEightAppBindings::class,
        TwentyFortyEightGraphTestBindings::class,
    ],
)
internal interface TwentyFortyEightPluginTestGraph {
    val registry: MiniAppRegistry
}

@ContributesTo(AppScope::class)
@BindingContainer
internal object TwentyFortyEightGraphTestBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalytics(): AnalyticRepository = NoOpAnalytics

    @Provides
    @SingleIn(AppScope::class)
    fun provideCrashlytics(): CrashlyticsRepository = NoOpCrashlytics
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
        MiniAppContractAssertions.assertBackNotConsumed(session)
        lifecycle.destroy()
    }
}

private data object NoOpAnalytics : AnalyticRepository {
    override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
    override fun deleteData() = Unit
}

private data object NoOpCrashlytics : CrashlyticsRepository {
    override fun setUserID(id: String) = Unit
    override fun clearUserID() = Unit
    override fun setCustomValue(key: String, value: Any) = Unit
    override fun logException(throwable: Throwable) = Unit
    override fun logMessage(message: String) = Unit
}
