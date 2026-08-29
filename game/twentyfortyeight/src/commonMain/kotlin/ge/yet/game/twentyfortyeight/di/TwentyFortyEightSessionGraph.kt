package ge.yet.game.twentyfortyeight.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.MiniAppSessionScope
import ge.yet.game.twentyfortyeight.TwentyFortyEightSession

@GraphExtension(MiniAppSessionScope::class)
interface TwentyFortyEightSessionGraph {
    val session: TwentyFortyEightSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createGameTwentyfortyeightSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): TwentyFortyEightSessionGraph
    }
}
