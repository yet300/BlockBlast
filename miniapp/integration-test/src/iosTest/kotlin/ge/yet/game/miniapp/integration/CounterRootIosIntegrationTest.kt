package ge.yet.game.miniapp.integration

import com.app.common.di.CommonBindings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraphFactory
import ge.yet.game.data.di.DataBindings
import ge.yet.game.data.di.NativeDataBindings
import ge.yet.game.blockblast.di.BlockBlastSessionGraph
import ge.yet.game.feature.catalog.di.CatalogBindings
import ge.yet.game.feature.review.di.ReviewBindings
import ge.yet.game.feature.root.di.RootBindings
import ge.yet.game.feature.settings.di.SettingsBindings
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.sample.counter.CounterSessionGraph
import kotlin.test.Test

@DependencyGraph(
    scope = AppScope::class,
    excludes = [MiniAppMetroBindings::class],
    bindingContainers = [
        CommonBindings::class,
        DataBindings::class,
        NativeDataBindings::class,
        RootBindings::class,
        CatalogBindings::class,
        ReviewBindings::class,
        SettingsBindings::class,
        CounterRootHostBindings::class,
        CounterRootRegistryBindings::class,
    ],
)
internal interface IosCounterRootGraph : CounterRootTestGraph {
    val blockBlastSessionFactory: BlockBlastSessionGraph.Factory
    val counterSessionFactory: CounterSessionGraph.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): IosCounterRootGraph
    }
}

internal fun createIosCounterRootGraph(): CounterRootTestGraph =
    createGraphFactory<IosCounterRootGraph.Factory>().create()

class CounterRootIosIntegrationTest {
    private val contract = CounterRootContract(::createIosCounterRootGraph)

    @Test
    fun blockblast_and_counter_session_factories_coexist_in_one_final_graph() {
        val graph = createIosCounterRootGraph() as IosCounterRootGraph

        kotlin.test.assertSame<Any>(graph.blockBlastSessionFactory, graph.counterSessionFactory)
    }

    @Test
    fun catalog_play_creates_real_counter_session() =
        contract.catalog_play_creates_real_counter_session()

    @Test
    fun settings_keeps_the_same_counter_session_and_reports_obscured() =
        contract.settings_keeps_the_same_counter_session_and_reports_obscured()

    @Test
    fun system_back_dismisses_settings_before_destroying_counter() =
        contract.system_back_dismisses_settings_before_destroying_counter()

    @Test
    fun background_reports_background_then_returns_active() =
        contract.background_reports_background_then_returns_active()

    @Test
    fun back_returns_catalog_and_destroys_counter_lifecycle_once() =
        contract.back_returns_catalog_and_destroys_counter_lifecycle_once()

    @Test
    fun stale_counter_host_callback_cannot_close_a_later_session() =
        contract.stale_counter_host_callback_cannot_close_a_later_session()
}
