package ge.yet.blockblast.feature.home

import com.app.common.decompose.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.lifecycle.doOnStart
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.home.integration.stateToModel
import ge.yet.blockblast.feature.home.store.HomeStore
import ge.yet.blockblast.feature.home.store.HomeStoreFactory

internal class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val homeStoreFactory: HomeStoreFactory,

    private val onContinueClickedCb: (Boolean) -> Unit,
    private val onNewGameClickedCb: (Boolean) -> Unit,
) : ComponentContext by componentContext,
    HomeComponent {
    private val store = instanceKeeper.getStore { homeStoreFactory.create() }

    override val model: Value<HomeComponent.Model> =
        store.asValue().map(stateToModel)


    init {
        lifecycle.doOnStart { store.accept(HomeStore.Intent.Refresh) }
    }

    override fun onContinueClicked() = onContinueClickedCb(false)
    override fun onNewGameClicked() = onNewGameClickedCb(true)
}

@Inject
internal class DefaultHomeComponentFactory(
    private val homeStoreFactory: HomeStoreFactory,
) : HomeComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        onContinueClicked: (Boolean) -> Unit,
        onNewGameClicked: (Boolean) -> Unit,
    ): HomeComponent = DefaultHomeComponent(
        componentContext = componentContext,
        homeStoreFactory = homeStoreFactory,
        onContinueClickedCb = onContinueClicked,
        onNewGameClickedCb = onNewGameClicked,
    )
}
