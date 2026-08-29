package ge.yet.game.fruitmerge

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnDestroy

interface FruitmergeComponent {
    val model: Value<Model>

    fun dispatch(action: FruitmergeGameAction)

    data class Model(
        val state: FruitmergeGameState = FruitmergeGameState(),
    )
}

internal class DefaultFruitmergeComponent(
    componentContext: ComponentContext,
    private val engine: FruitmergeGameEngine = DefaultFruitmergeGameEngine,
) : FruitmergeComponent, ComponentContext by componentContext {
    private val mutableModel = MutableValue(FruitmergeComponent.Model())
    override val model: Value<FruitmergeComponent.Model> = mutableModel

    init { componentContext.lifecycle.doOnDestroy { } }

    override fun dispatch(action: FruitmergeGameAction) {
        mutableModel.update { current ->
            current.copy(state = engine.reduce(current.state, action))
        }
    }
}
