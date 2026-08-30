package ge.yet.game.fruitmerge

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@GraphExtension(MiniAppSessionScope::class)
interface FruitMergeSessionGraph {
    val session: FruitMergeSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createGameFruitmergeSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): FruitMergeSessionGraph
    }
}
