package ge.yet.game.twentyfortyeight

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@GraphExtension(MiniAppSessionScope::class)
interface TwentyFortyEightSessionGraph {
    val session: TwentyFortyEightSession

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideComponent(componentContext: ComponentContext): TwentyFortyEightComponent =
        DefaultTwentyFortyEightComponent(componentContext)

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideSession(component: TwentyFortyEightComponent): TwentyFortyEightSession =
        TwentyFortyEightSession(component)

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createGameTwentyfortyeightSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): TwentyFortyEightSessionGraph
    }
}
