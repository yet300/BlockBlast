package ge.yet.game.twentyfortyeight.di

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.game.miniapp.metro.MiniAppSessionScope
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.NoopMiniAppAudio
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import ge.yet.game.twentyfortyeight.TwentyFortyEightGraphTestBindings
import ge.yet.game.twentyfortyeight.TwentyFortyEightSession
import ge.yet.game.twentyfortyeight.analytics.TwentyFortyEightAnalytics
import ge.yet.game.twentyfortyeight.audio.TwentyFortyEightAudioAdapter
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightDiagnostics
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.persistence.SessionPersistenceCoordinator
import ge.yet.game.twentyfortyeight.persistence.TwentyFortyEightPersistence
import ge.yet.game.twentyfortyeight.session.DefaultTwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionAdapter
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionPorts
import ge.yet.game.twentyfortyeight.store.NewGameSeedSource
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        MiniAppMetroBindings::class,
        TwentyFortyEightAppBindings::class,
        TwentyFortyEightGraphTestBindings::class,
    ],
)
internal interface InspectableTwentyFortyEightAppGraph {
    val registry: MiniAppRegistry
    val factory: InspectableTwentyFortyEightSessionGraph.Factory
    val persistence: TwentyFortyEightPersistence
    val analytics: TwentyFortyEightAnalytics
    val diagnostics: TwentyFortyEightDiagnostics
    val engine: MoveEngine
    val seedSource: NewGameSeedSource
}

@GraphExtension(MiniAppSessionScope::class)
internal interface InspectableTwentyFortyEightSessionGraph {
    val session: TwentyFortyEightSession
    val component: TwentyFortyEightSessionComponent
    val concreteComponent: DefaultTwentyFortyEightSessionComponent
    val store: TwentyFortyEightStore
    val coordinator: SessionPersistenceCoordinator
    val adapter: TwentyFortyEightSessionAdapter
    val audioAdapter: TwentyFortyEightAudioAdapter
    val ports: TwentyFortyEightSessionPorts
    val persistence: TwentyFortyEightPersistence
    val analytics: TwentyFortyEightAnalytics
    val diagnostics: TwentyFortyEightDiagnostics
    val engine: MoveEngine
    val seedSource: NewGameSeedSource
    val componentContext: ComponentContext
    val visibility: MiniAppVisibilitySource
    val host: MiniAppSessionHost
    val storage: MiniAppStorage
    val audio: MiniAppAudio
    val context: MiniAppSessionContext

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createInspectableTwentyFortyEightSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): InspectableTwentyFortyEightSessionGraph
    }
}

class TwentyFortyEightSessionGraphTest {
    @Test
    fun `two child graphs share only app scoped stateless services`() {
        val app = createGraph<InspectableTwentyFortyEightAppGraph>()
        val firstLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val secondLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val firstStorage = MutableMiniAppStorage()
        val secondStorage = MutableMiniAppStorage()
        val firstContext = TestMiniAppSessionContext(
            firstLifecycle.componentContext,
            MutableMiniAppVisibilitySource(),
            RecordingMiniAppSessionHost(),
            firstStorage,
            TestAudio(),
        )
        val secondContext = TestMiniAppSessionContext(
            secondLifecycle.componentContext,
            MutableMiniAppVisibilitySource(),
            RecordingMiniAppSessionHost(),
            secondStorage,
            TestAudio(),
        )

        val first = app.factory.createInspectableTwentyFortyEightSessionGraph(firstContext)
        val second = app.factory.createInspectableTwentyFortyEightSessionGraph(secondContext)

        assertSame(first.component, first.concreteComponent)
        assertSame(first.store, first.concreteComponent.retainedStore)
        assertSame(first.session.component, first.component)
        assertNotSame(first.session, second.session)
        assertNotSame(first.component, second.component)
        assertNotSame(first.store, second.store)
        assertNotSame(first.coordinator, second.coordinator)
        assertNotSame(first.adapter, second.adapter)
        assertNotSame(first.audioAdapter, second.audioAdapter)
        assertNotSame(first.ports, second.ports)

        assertSame(app.persistence, first.persistence)
        assertSame(app.analytics, first.analytics)
        assertSame(app.diagnostics, first.diagnostics)
        assertSame(app.engine, first.engine)
        assertSame(app.seedSource, first.seedSource)
        assertSame(first.persistence, second.persistence)
        assertSame(first.analytics, second.analytics)
        assertSame(first.diagnostics, second.diagnostics)
        assertSame(first.engine, second.engine)
        assertSame(first.seedSource, second.seedSource)

        assertSame(firstContext.componentContext, first.componentContext)
        assertSame(firstContext, first.context)
        assertSame(firstContext.visibility, first.visibility)
        assertSame(firstContext.host, first.host)
        assertSame(firstContext.storage, first.storage)
        assertSame(firstContext.audio, first.audio)
        assertSame(firstStorage, first.storage)
        assertSame(secondStorage, second.storage)
        assertSame(secondContext, second.context)
        assertNotSame(first.storage, second.storage)
        assertNotSame(first.visibility, second.visibility)
        assertNotSame(first.host, second.host)
        assertNotSame(first.audio, second.audio)

        firstLifecycle.destroy()
        secondLifecycle.destroy()
    }
}

private class TestAudio : MiniAppAudio by NoopMiniAppAudio
