package ge.yet.game.fruitmerge.session

import com.app.common.decompose.asValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.store.FruitMergeStore

internal interface FruitMergeResultComponent {
    val model: Value<Model>

    fun newGame()

    data class Model(
        val score: Long,
        val bestScore: Long,
        val largestFruit: FruitLevel,
    )
}

internal class DefaultFruitMergeResultComponent(
    store: FruitMergeStore,
    private val onNewGame: () -> Unit,
) : FruitMergeResultComponent {
    override val model: Value<FruitMergeResultComponent.Model> = store.asValue().map { state ->
        FruitMergeResultComponent.Model(
            score = state.game.score,
            bestScore = state.game.bestScore,
            largestFruit = state.game.bodies.maxByOrNull { body -> body.level.ordinal }?.level
                ?: state.game.previewLevel,
        )
    }

    override fun newGame() = onNewGame()
}
