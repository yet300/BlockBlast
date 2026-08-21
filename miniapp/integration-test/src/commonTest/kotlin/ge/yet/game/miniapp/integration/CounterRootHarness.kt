package ge.yet.game.miniapp.integration

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.feature.settings.libraries.LibrariesProvider
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppInterstitialGate
import ge.yet.game.miniapp.compose.MiniAppInterstitialPlacement
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.metro.DefaultMiniAppRegistry
import ge.yet.game.telemetry.di.TelemetryBindings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal val COUNTER_ID = MiniAppId("sample.counter")

internal interface CounterRootTestGraph {
    val rootFactory: RootComponent.Factory
    val counterProbe: CounterRootProbe
}

@BindingContainer
internal abstract class CounterRootRegistryBindings {
    @Multibinds(allowEmpty = true)
    abstract fun plugins(): Set<MiniAppPlugin>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun counterProbe(): CounterRootProbe = CounterRootProbe()

        @Provides
        @SingleIn(AppScope::class)
        fun registry(
            plugins: Set<MiniAppPlugin>,
            counterProbe: CounterRootProbe,
        ): MiniAppRegistry = DefaultMiniAppRegistry(
            plugins = plugins
                .filter { it.manifest.id == COUNTER_ID }
                .mapTo(mutableSetOf(), counterProbe::observe),
            expectations = emptySet(),
        )
    }
}

@ContributesTo(
    scope = AppScope::class,
    replaces = [TelemetryBindings::class],
)
@BindingContainer
internal object CounterRootHostBindings {
    @Provides
    fun librariesProvider(): LibrariesProvider = LibrariesProvider { emptyList() }

    @Provides
    fun interstitialCapability(): MiniAppInterstitialCapability =
        object : MiniAppInterstitialCapability {
            @Composable
            override fun rememberGate(
                placement: MiniAppInterstitialPlacement,
            ): MiniAppInterstitialGate = MiniAppInterstitialGate(
                willShowAd = false,
                request = { onComplete -> onComplete() },
            )
        }

    @Provides
    @SingleIn(AppScope::class)
    fun analyticRepository(): AnalyticRepository = object : AnalyticRepository {
        override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit

        override fun deleteData() = Unit
    }
}

internal class CounterRootProbe {
    var createCount: Int = 0
        private set
    var destroyCount: Int = 0
        private set
    val sessions = mutableListOf<MiniAppSession>()
    val visibilitySources = mutableListOf<MiniAppVisibilitySource>()
    val hosts = mutableListOf<MiniAppSessionHost>()

    fun observe(plugin: MiniAppPlugin): MiniAppPlugin = object : MiniAppPlugin {
        override val manifest = plugin.manifest

        override fun createSession(
            componentContext: ComponentContext,
            visibility: MiniAppVisibilitySource,
            host: MiniAppSessionHost,
        ): MiniAppSession {
            createCount += 1
            visibilitySources += visibility
            hosts += host
            componentContext.lifecycle.doOnDestroy { destroyCount += 1 }
            return plugin.createSession(componentContext, visibility, host).also(sessions::add)
        }
    }
}

internal class CounterRootHarness(
    graph: CounterRootTestGraph,
) {
    private val lifecycle = LifecycleRegistry()
    val backDispatcher = BackDispatcher()
    val probe = graph.counterProbe
    val root = graph.rootFactory.create(
        DefaultComponentContext(
            lifecycle = lifecycle,
            backHandler = backDispatcher,
        ),
    )

    fun resume() = lifecycle.resume()

    fun stop() = lifecycle.stop()

    fun destroy() = lifecycle.destroy()

    fun play() {
        catalog().component.onPlayClicked(COUNTER_ID)
    }

    fun catalog(): RootComponent.Child.Catalog = assertIs(root.stack.value.active.instance)

    fun running(): RootComponent.Child.RunningMiniApp = assertIs(root.stack.value.active.instance)

    fun session(): MiniAppSession =
        assertIs<RootComponent.MiniAppState.Content>(running().state).session

    fun visibility(): StateFlow<MiniAppVisibility> = probe.visibilitySources.single().visibility
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class CounterRootContract(
    private val createGraph: () -> CounterRootTestGraph,
) {
    fun catalog_play_creates_real_counter_session() = withHarness { harness ->
        assertEquals(listOf(COUNTER_ID), harness.catalog().component.model.value.manifests.map { it.id })

        harness.play()

        assertSame(harness.probe.sessions.single(), harness.session())
        assertEquals(1, harness.probe.createCount)
        assertEquals(MiniAppVisibility.ACTIVE, harness.visibility().value)
    }

    fun settings_keeps_the_same_counter_session_and_reports_obscured() = withHarness { harness ->
        harness.play()
        val session = harness.session()

        harness.root.onSettingsClicked()

        assertIs<RootComponent.SheetChild.Settings>(harness.root.sheetSlot.value.child?.instance)
        assertSame(session, harness.session())
        assertEquals(MiniAppVisibility.OBSCURED, harness.visibility().value)

        harness.root.onDismissSheet()
        assertSame(session, harness.session())
        assertEquals(MiniAppVisibility.ACTIVE, harness.visibility().value)
    }

    fun system_back_dismisses_settings_before_destroying_counter() = withHarness { harness ->
        harness.play()
        val session = harness.session()
        harness.root.onSettingsClicked()

        assertTrue(harness.backDispatcher.back())

        assertNull(harness.root.sheetSlot.value.child)
        assertSame(session, harness.session())
        assertEquals(0, harness.probe.destroyCount)
    }

    fun background_reports_background_then_returns_active() = withHarness { harness ->
        harness.play()
        val session = harness.session()

        harness.stop()
        assertEquals(MiniAppVisibility.BACKGROUND, harness.visibility().value)
        harness.resume()

        assertSame(session, harness.session())
        assertEquals(MiniAppVisibility.ACTIVE, harness.visibility().value)
        assertEquals(1, harness.probe.createCount)
    }

    fun back_returns_catalog_and_destroys_counter_lifecycle_once() = withHarness { harness ->
        harness.play()

        harness.root.onBackClicked()
        harness.root.onBackClicked()

        assertIs<RootComponent.Child.Catalog>(harness.root.stack.value.active.instance)
        assertEquals(1, harness.probe.destroyCount)
    }

    fun stale_counter_host_callback_cannot_close_a_later_session() = withHarness { harness ->
        harness.play()
        val staleHost = harness.probe.hosts.single()
        harness.root.onBackClicked()
        harness.play()
        val laterSession = harness.session()

        staleHost.close()
        runCurrent()

        assertSame(laterSession, harness.session())
        assertEquals(2, harness.probe.createCount)
        assertEquals(1, harness.probe.destroyCount)
    }

    private fun withHarness(block: suspend TestScope.(CounterRootHarness) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val harness = CounterRootHarness(createGraph())
        try {
            harness.resume()
            runCurrent()
            block(harness)
        } finally {
            harness.destroy()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
