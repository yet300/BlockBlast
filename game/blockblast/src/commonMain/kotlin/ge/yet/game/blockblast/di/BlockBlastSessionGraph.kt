package ge.yet.game.blockblast.di

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import ge.yet.game.blockblast.session.BlockBlastSession
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@GraphExtension(MiniAppSessionScope::class)
interface BlockBlastSessionGraph {
    val session: BlockBlastSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createGameBlockblastSessionGraph(
            @Provides componentContext: ComponentContext,
            @Provides visibility: MiniAppVisibilitySource,
            @Provides host: MiniAppSessionHost,
        ): BlockBlastSessionGraph
    }
}
