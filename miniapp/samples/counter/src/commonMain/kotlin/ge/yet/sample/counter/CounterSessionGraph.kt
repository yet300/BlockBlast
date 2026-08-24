package ge.yet.sample.counter

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@GraphExtension(MiniAppSessionScope::class)
interface CounterSessionGraph {
    val session: CounterSession

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideComponent(
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
        audio: MiniAppAudio,
    ): CounterComponent = DefaultCounterComponent(componentContext, visibility, audio)

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideSession(
        component: CounterComponent,
        host: MiniAppSessionHost,
    ): CounterSession = CounterSession(component, host)

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createSampleCounterSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): CounterSessionGraph
    }
}
