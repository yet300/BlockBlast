package ge.yet.sample.counter

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.metro.RetainedMiniAppSession
import ge.yet.miniapp.samples.counter.generated.resources.Res
import ge.yet.miniapp.samples.counter.generated.resources.miniapp_description
import ge.yet.miniapp.samples.counter.generated.resources.miniapp_icon
import ge.yet.miniapp.samples.counter.generated.resources.miniapp_title

@Inject
@ContributesIntoSet(AppScope::class)
class CounterPlugin(private val graphFactory: CounterSessionGraph.Factory) : MiniAppPlugin {
    override val manifest = MiniAppManifest(
        id = MiniAppId("sample.counter"),
        title = Res.string.miniapp_title,
        description = Res.string.miniapp_description,
        icon = Res.drawable.miniapp_icon,
        cover = null,
        category = MiniAppCategoryId("sample"),
        sortPriority = 0,
    )

    override fun createSession(
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
        host: MiniAppSessionHost,
    ): MiniAppSession {
        val graph = graphFactory.createSample_CounterSessionGraph(componentContext, visibility, host)
        return RetainedMiniAppSession(graph, graph.session)
    }
}
