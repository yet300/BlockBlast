package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MutableMiniAppVisibilitySource(
    initial: MiniAppVisibility = MiniAppVisibility.ACTIVE,
) : MiniAppVisibilitySource {
    private val mutableVisibility = MutableStateFlow(initial)
    override val visibility: StateFlow<MiniAppVisibility> = mutableVisibility.asStateFlow()

    fun set(value: MiniAppVisibility) {
        mutableVisibility.value = value
    }
}
