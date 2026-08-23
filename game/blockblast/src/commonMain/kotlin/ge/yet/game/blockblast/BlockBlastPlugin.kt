package ge.yet.game.blockblast

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import ge.yet.game.blockblast.data.repository.BLOCK_BLAST_ID
import ge.yet.game.blockblast.di.BlockBlastSessionGraph
import ge.yet.game.blockblast.generated.resources.Res
import ge.yet.game.blockblast.generated.resources.miniapp_description
import ge.yet.game.blockblast.generated.resources.miniapp_icon
import ge.yet.game.blockblast.generated.resources.miniapp_title
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.RetainedMiniAppSession

@Inject
@ContributesIntoSet(AppScope::class)
class BlockBlastPlugin(
    private val graphFactory: BlockBlastSessionGraph.Factory,
) : MiniAppPlugin {
    override val manifest = MiniAppManifest(
        id = BLOCK_BLAST_ID,
        title = Res.string.miniapp_title,
        description = Res.string.miniapp_description,
        icon = Res.drawable.miniapp_icon,
        cover = null,
        category = MiniAppCategoryId("game"),
        sortPriority = 0,
    )

    override fun createSession(context: MiniAppSessionContext): MiniAppSession {
        val graph = graphFactory.createGameBlockblastSessionGraph(context)
        return RetainedMiniAppSession(graph, graph.session)
    }
}
