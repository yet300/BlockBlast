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
        publish(TwentyFortyEightSessionComponent.Effect.Announcement(effectIds.next(), fact))
    override fun requestFocus(target: FocusTarget) =
        publish(TwentyFortyEightSessionComponent.Effect.Focus(effectIds.next(), target))
    override fun showError(code: UiErrorCode) =
        publish(TwentyFortyEightSessionComponent.Effect.Error(effectIds.next(), code))

    private fun publish(effect: TwentyFortyEightSessionComponent.Effect) {
        mutableEffect.value = TwentyFortyEightSessionComponent.EffectState(effect)
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
