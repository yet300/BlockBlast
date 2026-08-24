package ge.yet.game.blockblast.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import ge.yet.game.blockblast.session.BlockBlastSession
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@GraphExtension(MiniAppSessionScope::class)
interface BlockBlastSessionGraph {
    val session: BlockBlastSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createGameBlockblastSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): BlockBlastSessionGraph
    }
}
