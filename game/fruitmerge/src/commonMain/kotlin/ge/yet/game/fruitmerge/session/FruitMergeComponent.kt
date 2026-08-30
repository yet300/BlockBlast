package ge.yet.game.fruitmerge.session

import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.extensions.coroutines.states
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.TargetingMode
import ge.yet.game.fruitmerge.store.FruitMergeStore
import ge.yet.game.fruitmerge.store.FruitMergeStoreFactory
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal enum class PaidAction {
    CLEAR,
    SHAKE,
}

internal data class PaidActionToken(
    val sessionKey: Long,
    val runOrdinal: Long,
    val id: Long,
    val action: PaidAction,
)

internal interface FruitMergeComponent {
    val model: Value<Model>

    fun frame(elapsedSeconds: Float)
    fun movePreview(x: Float)
    fun drop()
    fun requestClearGate(): PaidActionToken?
    fun selectClearTarget(id: Long)
    fun cancelClear()
    fun requestShakeGate(): PaidActionToken?
    fun completePaidAction(token: PaidActionToken)
    fun newGame()
    fun handleBack(): Boolean

    data class Model(
        val game: FruitMergeState = FruitMergeState(),
        val initialized: Boolean = false,
        val visible: Boolean = true,
    )
}

@OptIn(DelicateDecomposeApi::class)
internal class DefaultFruitMergeComponent(
    componentContext: ComponentContext,
    storeFactory: FruitMergeStoreFactory,
    private val visibility: MiniAppVisibilitySource,
) : FruitMergeComponent,
    ComponentContext by componentContext {
    private val store: FruitMergeStore = instanceKeeper.getStore(storeFactory::create)
    private val mutableModel = MutableValue(
        FruitMergeComponent.Model(
            game = store.state.game,
            initialized = store.state.initialized,
            visible = visibility.visibility.value == MiniAppVisibility.ACTIVE,
        ),
    )
    override val model: Value<FruitMergeComponent.Model> = mutableModel

    private val sessionKey = componentContext.hashCode().toLong()
    private var nextTokenId = 1L
    private var pendingToken: PaidActionToken? = null
    private var pendingClearPaid = false
    private var alive = true

    init {
        val scope = coroutineScope()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.states.collect { state ->
                mutableModel.value = mutableModel.value.copy(
                    game = state.game,
                    initialized = state.initialized,
                )
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            visibility.visibility.collect { value ->
                val active = value == MiniAppVisibility.ACTIVE
                mutableModel.value = mutableModel.value.copy(visible = active)
                store.accept(FruitMergeStore.Intent.VisibilityChanged(active))
            }
        }
        lifecycle.doOnDestroy {
            alive = false
            pendingToken = null
        }
    }

    override fun frame(elapsedSeconds: Float) {
        if (alive && model.value.visible) store.accept(FruitMergeStore.Intent.Frame(elapsedSeconds))
    }

    override fun movePreview(x: Float) {
        if (alive) store.accept(FruitMergeStore.Intent.MovePreview(x))
    }

    override fun drop() {
        if (alive) store.accept(FruitMergeStore.Intent.Drop)
    }

    override fun requestClearGate(): PaidActionToken? {
        if (!alive || !model.value.initialized || pendingToken != null) return null
        if (model.value.game.targetingMode == TargetingMode.CLEAR) return null
        if (model.value.game.freeClears > 0) {
            pendingClearPaid = false
            store.accept(FruitMergeStore.Intent.BeginFreeClear)
            return null
        }
        return createToken(PaidAction.CLEAR)
    }

    override fun selectClearTarget(id: Long) {
        if (!alive || model.value.game.targetingMode != TargetingMode.CLEAR) return
        store.accept(FruitMergeStore.Intent.ClearBody(id, paid = pendingClearPaid))
        pendingClearPaid = false
    }

    override fun cancelClear() {
        pendingClearPaid = false
        if (alive) store.accept(FruitMergeStore.Intent.CancelClear)
    }

    override fun requestShakeGate(): PaidActionToken? {
        if (!alive || !model.value.initialized || pendingToken != null) return null
        if (model.value.game.freeShakes > 0) {
            store.accept(FruitMergeStore.Intent.FreeShake)
            return null
        }
        return createToken(PaidAction.SHAKE)
    }

    override fun completePaidAction(token: PaidActionToken) {
        if (!alive || token != pendingToken || token.sessionKey != sessionKey) return
        if (token.runOrdinal != model.value.game.runOrdinal) {
            pendingToken = null
            return
        }
        pendingToken = null
        when (token.action) {
            PaidAction.CLEAR -> {
                pendingClearPaid = true
                store.accept(FruitMergeStore.Intent.PaidClear)
            }
            PaidAction.SHAKE -> store.accept(FruitMergeStore.Intent.PaidShake)
        }
    }

    override fun newGame() {
        if (!alive) return
        pendingToken = null
        pendingClearPaid = false
        store.accept(FruitMergeStore.Intent.NewGame)
    }

    override fun handleBack(): Boolean = if (model.value.game.targetingMode == TargetingMode.CLEAR) {
        cancelClear()
        true
    } else {
        false
    }

    private fun createToken(action: PaidAction): PaidActionToken {
        val token = PaidActionToken(
            sessionKey = sessionKey,
            runOrdinal = model.value.game.runOrdinal,
            id = nextTokenId,
            action = action,
        )
        if (nextTokenId < Long.MAX_VALUE) nextTokenId += 1L
        pendingToken = token
        return token
    }
}
