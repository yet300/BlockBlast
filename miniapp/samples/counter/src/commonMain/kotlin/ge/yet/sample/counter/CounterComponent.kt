package ge.yet.sample.counter

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnDestroy
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import kotlinx.coroutines.flow.StateFlow

interface CounterComponent {
    val model: Value<Model>
    val visibility: StateFlow<MiniAppVisibility>

    fun onIncrementClicked()

    data class Model(val count: Int = 0)
}

internal class DefaultCounterComponent(
    componentContext: ComponentContext,
    visibilitySource: MiniAppVisibilitySource,
) : CounterComponent, ComponentContext by componentContext {
    private val mutableModel = MutableValue(CounterComponent.Model())
    override val model: Value<CounterComponent.Model> = mutableModel
    override val visibility: StateFlow<MiniAppVisibility> = visibilitySource.visibility

    internal var destroyCount: Int = 0
        private set

    init {
        lifecycle.doOnDestroy { destroyCount += 1 }
    }

    override fun onIncrementClicked() {
        mutableModel.update { current ->
            current.copy(count = current.count + 1)
        }
    }
}
