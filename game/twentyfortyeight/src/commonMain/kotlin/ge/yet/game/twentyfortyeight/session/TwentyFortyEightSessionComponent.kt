package ge.yet.game.twentyfortyeight.session

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
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.twentyfortyeight.component.DefaultPlayingComponent
import ge.yet.game.twentyfortyeight.component.DefaultResultComponent
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.component.ResultComponent
import ge.yet.game.twentyfortyeight.engine.GamePhase
import ge.yet.game.twentyfortyeight.engine.GameState
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.store.AnnouncementFact
import ge.yet.game.twentyfortyeight.store.FocusTarget
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStoreFactory
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal interface TwentyFortyEightSessionComponent {
    val stack: Value<ChildStack<*, Child>>
    val frameMode: Value<MiniAppFrameMode>
    val effect: Value<EffectState>
    fun onEffectConsumed(effectId: Long)
    fun handleBack(): Boolean

    data class EffectState(val effects: List<Effect> = emptyList()) {
        init {
            require(effects.size <= MaxPendingEffects) {
                "Too many pending UI effects: ${effects.size}"
            }
            require(effects.map { it.id }.distinct().size == effects.size) {
                "Pending UI effect IDs must be unique"
            }
        }

        val effect: Effect?
            get() = effects.firstOrNull()
    }

    sealed interface Effect {
        val id: Long

        data class Announcement(override val id: Long, val fact: AnnouncementFact) : Effect
        data class Focus(override val id: Long, val target: FocusTarget) : Effect
        data class Error(override val id: Long, val code: UiErrorCode) : Effect
    }

    sealed interface Child {
        class Playing(val component: PlayingComponent) : Child
        class Result(val component: ResultComponent) : Child
    }

    companion object {
        const val MaxPendingEffects: Int = 16
    }
}

@OptIn(DelicateDecomposeApi::class)
internal class DefaultTwentyFortyEightSessionComponent(
    componentContext: ComponentContext,
    storeFactory: TwentyFortyEightStoreFactory,
    adapter: TwentyFortyEightSessionAdapter,
    private val ports: TwentyFortyEightSessionPorts,
) : TwentyFortyEightSessionComponent,
    ComponentContext by componentContext {

    internal val retainedStore: TwentyFortyEightStore = instanceKeeper.getStore(storeFactory::create)
    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, TwentyFortyEightSessionComponent.Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = retainedStore.state.toInitialConfig(),
        handleBackButton = false,
        childFactory = ::createChild,
    )

    override val frameMode: Value<MiniAppFrameMode> = stack.map { MiniAppFrameMode.Standard }
    override val effect: Value<TwentyFortyEightSessionComponent.EffectState> = ports.effect

    override fun onEffectConsumed(effectId: Long) = ports.consumeEffect(effectId)

    internal val labelCollector: Job

    init {
        ports.bind(::navigateToResult, ::onNewGameCommitted)
        labelCollector = coroutineScope().launch(start = CoroutineStart.UNDISPATCHED) {
            adapter.collect(retainedStore.labels)
        }
    }

    override fun handleBack(): Boolean =
        (stack.value.active.instance as? TwentyFortyEightSessionComponent.Child.Playing)
            ?.component
            ?.handleBack()
            ?: false

    internal fun navigateToResult(snapshot: ResultSnapshot) {
        if (stack.value.active.instance is TwentyFortyEightSessionComponent.Child.Result) return
        navigation.replaceAll(Config.Result(snapshot))
    }

    private fun onNewGameCommitted(runOrdinal: Long) {
        if (stack.value.active.instance !is TwentyFortyEightSessionComponent.Child.Result) return
        navigation.replaceAll(Config.Playing(runOrdinal))
    }

    private fun createChild(config: Config, componentContext: ComponentContext): TwentyFortyEightSessionComponent.Child =
        when (config) {
            is Config.Playing -> TwentyFortyEightSessionComponent.Child.Playing(
                DefaultPlayingComponent(componentContext, retainedStore),
            )
            is Config.Result -> TwentyFortyEightSessionComponent.Child.Result(
                DefaultResultComponent(config.snapshot) {
                    retainedStore.accept(TwentyFortyEightStore.Intent.NewGameFromResult)
                },
            )
        }
}

private sealed interface Config {
    data class Playing(val runOrdinal: Long) : Config
    data class Result(val snapshot: ResultSnapshot) : Config
}

private fun TwentyFortyEightStore.State.toInitialConfig(): Config {
    val authoritativeGame = game
    return if (authoritativeGame?.phase == GamePhase.GameOver) {
        Config.Result(authoritativeGame.toResultSnapshot(statistics))
    } else {
        Config.Playing(authoritativeGame?.runOrdinal ?: 0L)
    }
}

private fun GameState.toResultSnapshot(statistics: GameStatistics) =
    ResultSnapshot(
        score = score,
        bestScore = bestScore,
        highestTile = board.values().filterNotNull().maxOrNull() ?: 0L,
        statistics = statistics,
    )
