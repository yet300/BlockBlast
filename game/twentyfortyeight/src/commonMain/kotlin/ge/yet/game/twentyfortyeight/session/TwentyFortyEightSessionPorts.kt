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
    private val mutableEffect = MutableValue(EffectState())
    private var nextEffectId = 1L

    val effect: Value<EffectState> = mutableEffect

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

    override fun announce(fact: AnnouncementFact) = publish(Effect.Announcement(nextEffectId++, fact))
    override fun requestFocus(target: FocusTarget) = publish(Effect.Focus(nextEffectId++, target))
    override fun showError(code: UiErrorCode) = publish(Effect.Error(nextEffectId++, code))

    private fun publish(effect: Effect) {
        mutableEffect.value = EffectState(effect)
    }

    data class EffectState(val effect: Effect? = null)

    sealed interface Effect {
        val id: Long

        data class Announcement(override val id: Long, val fact: AnnouncementFact) : Effect
        data class Focus(override val id: Long, val target: FocusTarget) : Effect
        data class Error(override val id: Long, val code: UiErrorCode) : Effect
    }
}
