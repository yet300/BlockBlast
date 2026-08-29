package ge.yet.game.twentyfortyeight

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import ge.yet.game.miniapp.metro.RetainedMiniAppSession
import ge.yet.game.twentyfortyeight.di.TwentyFortyEightSessionGraph
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.miniapp_description
import ge.yet.game.twentyfortyeight.generated.resources.miniapp_icon
import ge.yet.game.twentyfortyeight.generated.resources.miniapp_title

@Inject
@ContributesIntoSet(AppScope::class)
class TwentyFortyEightPlugin(
    private val graphFactory: TwentyFortyEightSessionGraph.Factory,
) : MiniAppPlugin {
    override val manifest = MiniAppManifest(
        id = MiniAppId("game.twentyfortyeight"),
        title = Res.string.miniapp_title,
        description = Res.string.miniapp_description,
        icon = Res.drawable.miniapp_icon,
        cover = null,
        category = MiniAppCategoryId("game"),
        sortPriority = 100,
    )

    override fun createSession(context: MiniAppSessionContext): MiniAppSession {
        val graph = graphFactory.createGameTwentyfortyeightSessionGraph(context)
        return RetainedMiniAppSession(graph, graph.session)
    }
}
