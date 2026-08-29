package ge.yet.game.twentyfortyeight.component

import com.app.common.decompose.asValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore

internal interface ResultComponent {
    val model: Value<Model>

    fun onNewGameRequested()

    data class Model(
        val score: Long,
        val bestScore: Long,
        val highestTile: Long,
    )
}

internal class DefaultResultComponent(
    store: TwentyFortyEightStore,
    private val onNewGame: () -> Unit,
) : ResultComponent {
    override val model: Value<ResultComponent.Model> = store.asValue().map { state ->
        val game = state.game
        ResultComponent.Model(
            score = game?.score ?: 0L,
            bestScore = game?.bestScore ?: 0L,
            highestTile = game?.board?.values()?.filterNotNull()?.maxOrNull() ?: 0L,
        )
    }

    override fun onNewGameRequested() = onNewGame()
}
