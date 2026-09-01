package ge.yet.game.fruitmerge

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.miniapp_description
import ge.yet.game.fruitmerge.generated.resources.miniapp_icon
import ge.yet.game.fruitmerge.generated.resources.miniapp_title
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.RetainedMiniAppSession

@Inject
@ContributesIntoSet(AppScope::class)
class FruitMergePlugin(
    private val graphFactory: FruitMergeSessionGraph.Factory,
) : MiniAppPlugin {
    override val manifest = MiniAppManifest(
        id = MiniAppId("game.fruitmerge"),
        title = Res.string.miniapp_title,
        description = Res.string.miniapp_description,
        icon = Res.drawable.miniapp_icon,
        cover = null,
        category = MiniAppCategoryId("game"),
        sortPriority = 0,
    )

    override fun createSession(context: MiniAppSessionContext): MiniAppSession {
        val graph = graphFactory.createGameFruitmergeSessionGraph(context)
        return RetainedMiniAppSession(graph, graph.session)
    }
}
