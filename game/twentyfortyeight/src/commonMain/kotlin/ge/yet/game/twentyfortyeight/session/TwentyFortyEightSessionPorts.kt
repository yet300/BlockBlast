package ge.yet.game.twentyfortyeight.session

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.store.AnnouncementFact
import ge.yet.game.twentyfortyeight.store.FocusTarget
import ge.yet.game.twentyfortyeight.store.UiErrorCode

internal class TwentyFortyEightSessionPorts : SessionNavigation, SessionUiEffects {
    private var navigateToResult: ((ResultSnapshot) -> Unit)? = null
    private var onNewGameCommitted: ((Long) -> Unit)? = null
    private val mutableEffect = MutableValue(TwentyFortyEightSessionComponent.EffectState())
    private val effectIds = EffectIdAllocator()

    val effect: Value<TwentyFortyEightSessionComponent.EffectState> = mutableEffect

    fun bind(
        navigateToResult: (ResultSnapshot) -> Unit,
        onNewGameCommitted: (Long) -> Unit,
    ) {
        this.navigateToResult = navigateToResult
        this.onNewGameCommitted = onNewGameCommitted
    }

    override fun navigateToResult(snapshot: ResultSnapshot) {
        checkNotNull(navigateToResult)(snapshot)
    }

    override fun onNewGameCommitted(runOrdinal: Long) {
        checkNotNull(onNewGameCommitted)(runOrdinal)
    }

    override fun announce(fact: AnnouncementFact) =
        publish { id -> TwentyFortyEightSessionComponent.Effect.Announcement(id, fact) }
    override fun requestFocus(target: FocusTarget) =
        publish { id -> TwentyFortyEightSessionComponent.Effect.Focus(id, target) }
    override fun showError(code: UiErrorCode) =
        publish { id -> TwentyFortyEightSessionComponent.Effect.Error(id, code) }

    fun consumeEffect(effectId: Long) {
        val pending = mutableEffect.value.effects
        if (pending.firstOrNull()?.id != effectId) return
        mutableEffect.value = TwentyFortyEightSessionComponent.EffectState(pending.drop(1))
    }

    private inline fun publish(
        createEffect: (Long) -> TwentyFortyEightSessionComponent.Effect,
    ) {
        val pending = mutableEffect.value.effects
        check(pending.size < TwentyFortyEightSessionComponent.MaxPendingEffects) {
            "Pending UI effect capacity exceeded"
        }
        val effect = createEffect(effectIds.next())
        mutableEffect.value = TwentyFortyEightSessionComponent.EffectState(pending + effect)
    }
}

internal class EffectIdAllocator(initial: Long = 1L) {
    private var nextId = initial.also { require(it > 0L) }
    private var exhausted = false

    fun next(): Long {
        check(!exhausted) { "Effect ID space exhausted" }
        val allocated = nextId
        if (allocated == Long.MAX_VALUE) {
            exhausted = true
        } else {
            nextId = allocated + 1L
        }
        return allocated
    }
}
