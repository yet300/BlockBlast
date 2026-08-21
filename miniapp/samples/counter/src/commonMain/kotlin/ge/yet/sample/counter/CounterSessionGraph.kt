package ge.yet.sample.counter

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@GraphExtension(MiniAppSessionScope::class)
interface CounterSessionGraph {
    @Named("sample.counter.session")
    val session: MiniAppSession

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideComponent(
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
    ): CounterComponent = DefaultCounterComponent(componentContext, visibility)

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    @Named("sample.counter.session")
    fun provideSession(
        component: CounterComponent,
        host: MiniAppSessionHost,
    ): MiniAppSession = CounterSession(component, host)

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createSample_CounterSessionGraph(
            @Provides componentContext: ComponentContext,
            @Provides visibility: MiniAppVisibilitySource,
            @Provides host: MiniAppSessionHost,
        ): CounterSessionGraph
    }
}
