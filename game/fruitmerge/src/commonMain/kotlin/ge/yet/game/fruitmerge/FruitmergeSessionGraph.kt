package ge.yet.game.fruitmerge

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@GraphExtension(MiniAppSessionScope::class)
interface FruitmergeSessionGraph {
    val session: FruitmergeSession

    @Provides @SingleIn(MiniAppSessionScope::class)
    fun provideComponent(componentContext: ComponentContext): FruitmergeComponent = DefaultFruitmergeComponent(componentContext)

    @Provides @SingleIn(MiniAppSessionScope::class)
    fun provideSession(component: FruitmergeComponent): FruitmergeSession = FruitmergeSession(component)

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createGameFruitmergeSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): FruitmergeSessionGraph
    }
}
