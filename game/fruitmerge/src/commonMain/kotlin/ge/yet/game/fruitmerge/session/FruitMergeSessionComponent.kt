package ge.yet.game.fruitmerge.session

import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.states
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import ge.yet.game.fruitmerge.store.FruitMergeStore
import ge.yet.game.fruitmerge.store.FruitMergeStoreFactory
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

internal interface FruitMergeSessionComponent {
    val stack: Value<ChildStack<*, Child>>
    val frameMode: Value<MiniAppFrameMode>

    fun completePaidAction(token: PaidActionToken)
    fun handleBack(): Boolean

    sealed interface Child {
        class Playing(val component: FruitMergeComponent) : Child
        class Result(val component: FruitMergeResultComponent) : Child
    }
}

@OptIn(DelicateDecomposeApi::class)
internal class DefaultFruitMergeSessionComponent(
    componentContext: ComponentContext,
    storeFactory: FruitMergeStoreFactory,
    private val persistence: FruitMergePersistence,
    private val visibility: MiniAppVisibilitySource,
) : FruitMergeSessionComponent,
    ComponentContext by componentContext {
    internal val retainedStore: FruitMergeStore = instanceKeeper.getStore(storeFactory::create)
    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, FruitMergeSessionComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Playing(runOrdinal = 0L),
        handleBackButton = false,
        childFactory = ::createChild,
    )

    override val frameMode: Value<MiniAppFrameMode> = stack.map { childStack ->
        when (childStack.active.instance) {
            is FruitMergeSessionComponent.Child.Playing -> MiniAppFrameMode.Standard
            is FruitMergeSessionComponent.Child.Result -> MiniAppFrameMode.ContentOnly
        }
    }

    init {
        val scope = coroutineScope()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            retainedStore.labels.collect { label ->
                if (label == FruitMergeStore.Label.ResultReached) navigateToResult()
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            retainedStore.states.collect { state ->
                if (state.initialized) reconcile(state)
            }
        }
    }

    override fun completePaidAction(token: PaidActionToken) {
        activePlaying()?.completePaidAction(token)
    }

    override fun handleBack(): Boolean = activePlaying()?.handleBack() ?: false

    private fun reconcile(state: FruitMergeStore.State) {
        val activeConfig = stack.value.active.configuration as Config
        when {
            state.game.phase == RunPhase.RESULT && activeConfig !is Config.Result -> navigateToResult()
            state.game.phase == RunPhase.PLAYING &&
                (activeConfig !is Config.Playing || activeConfig.runOrdinal != state.game.runOrdinal) ->
                navigation.replaceAll(Config.Playing(state.game.runOrdinal))
        }
    }

    private fun navigateToResult() {
        val runOrdinal = retainedStore.state.game.runOrdinal
        val activeConfig = stack.value.active.configuration as Config
        if (activeConfig is Config.Result && activeConfig.runOrdinal == runOrdinal) return
        navigation.replaceAll(Config.Result(runOrdinal))
    }

    private fun activePlaying(): FruitMergeComponent? =
        (stack.value.active.instance as? FruitMergeSessionComponent.Child.Playing)?.component

    private fun createChild(
        config: Config,
        componentContext: ComponentContext,
    ): FruitMergeSessionComponent.Child = when (config) {
        is Config.Playing -> FruitMergeSessionComponent.Child.Playing(
            DefaultFruitMergeComponent(
                componentContext = componentContext,
                store = retainedStore,
                persistence = persistence,
                visibility = visibility,
            ),
        )
        is Config.Result -> FruitMergeSessionComponent.Child.Result(
            DefaultFruitMergeResultComponent(retainedStore) {
                retainedStore.accept(FruitMergeStore.Intent.NewGame)
            },
        )
    }
}

@Serializable
private sealed interface Config {
    val runOrdinal: Long

    @Serializable
    data class Playing(override val runOrdinal: Long) : Config

    @Serializable
    data class Result(override val runOrdinal: Long) : Config
}
