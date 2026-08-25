package ge.yet.game.twentyfortyeight.di

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.store.StoreFactory
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.metro.MiniAppSessionScope
import ge.yet.game.twentyfortyeight.TwentyFortyEightSession
import ge.yet.game.twentyfortyeight.analytics.TwentyFortyEightAnalytics
import ge.yet.game.twentyfortyeight.audio.TwentyFortyEightAudioAdapter
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightDiagnostics
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.persistence.SessionPersistenceCoordinator
import ge.yet.game.twentyfortyeight.persistence.TwentyFortyEightPersistence
import ge.yet.game.twentyfortyeight.session.DefaultTwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.session.SessionNavigation
import ge.yet.game.twentyfortyeight.session.SessionUiEffects
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionAdapter
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionPorts
import ge.yet.game.twentyfortyeight.store.NewGameSeedSource
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStoreFactory

@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
abstract class TwentyFortyEightSessionBindings {
    companion object {
        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideCoordinator(
            storage: MiniAppStorage,
            persistence: TwentyFortyEightPersistence,
        ): SessionPersistenceCoordinator = SessionPersistenceCoordinator(storage, persistence, persistence)

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideStoreFactory(
            storeFactory: StoreFactory,
            engine: MoveEngine,
            coordinator: SessionPersistenceCoordinator,
            visibility: MiniAppVisibilitySource,
            seedSource: NewGameSeedSource,
        ): TwentyFortyEightStoreFactory = TwentyFortyEightStoreFactory(
            storeFactory,
            engine,
            coordinator,
            visibility,
            seedSource,
        )

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun providePorts(): TwentyFortyEightSessionPorts = TwentyFortyEightSessionPorts()

        @Provides
        internal fun provideNavigation(ports: TwentyFortyEightSessionPorts): SessionNavigation = ports

        @Provides
        internal fun provideUiEffects(ports: TwentyFortyEightSessionPorts): SessionUiEffects = ports

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideAudioAdapter(audio: MiniAppAudio): TwentyFortyEightAudioAdapter =
            TwentyFortyEightAudioAdapter(audio)

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideAdapter(
            navigation: SessionNavigation,
            audio: TwentyFortyEightAudioAdapter,
            analytics: TwentyFortyEightAnalytics,
            diagnostics: TwentyFortyEightDiagnostics,
            host: MiniAppSessionHost,
            uiEffects: SessionUiEffects,
        ): TwentyFortyEightSessionAdapter = TwentyFortyEightSessionAdapter(
            navigation,
            audio,
            analytics,
            diagnostics,
            host,
            uiEffects,
        )

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideComponent(
            componentContext: ComponentContext,
            storeFactory: TwentyFortyEightStoreFactory,
            adapter: TwentyFortyEightSessionAdapter,
            ports: TwentyFortyEightSessionPorts,
        ): DefaultTwentyFortyEightSessionComponent = DefaultTwentyFortyEightSessionComponent(
            componentContext,
            storeFactory,
            adapter,
            ports,
        )

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideComponentContract(
            component: DefaultTwentyFortyEightSessionComponent,
        ): TwentyFortyEightSessionComponent = component

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideStore(component: DefaultTwentyFortyEightSessionComponent): TwentyFortyEightStore =
            component.retainedStore

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideSession(component: TwentyFortyEightSessionComponent): TwentyFortyEightSession =
            TwentyFortyEightSession(component)
    }
}
