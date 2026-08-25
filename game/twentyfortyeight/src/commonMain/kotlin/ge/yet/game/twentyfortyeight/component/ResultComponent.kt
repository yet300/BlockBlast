package ge.yet.game.twentyfortyeight.component

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot

internal interface ResultComponent {
    val model: Value<Model>

    fun onNewGameRequested()

    data class Model(
        val score: Long,
        val bestScore: Long,
        val highestTile: Long,
        val statistics: SelectedStatistics,
    )

    data class SelectedStatistics(
        val gamesStarted: Long,
        val gamesWon: Long,
        val gamesEndedByGameOver: Long,
        val successfulMoves: Long,
        val totalMerges: Long,
        val undoUses: Long,
    )
}

internal class DefaultResultComponent(
    snapshot: ResultSnapshot,
    private val onNewGame: () -> Unit,
) : ResultComponent {
    override val model: Value<ResultComponent.Model> = MutableValue(snapshot.toModel())

    override fun onNewGameRequested() = onNewGame()
}

private fun ResultSnapshot.toModel() = ResultComponent.Model(
    score = score,
    bestScore = bestScore,
    highestTile = highestTile,
    statistics = ResultComponent.SelectedStatistics(
        gamesStarted = statistics.gamesStarted,
        gamesWon = statistics.gamesWon,
        gamesEndedByGameOver = statistics.gamesEndedByGameOver,
        successfulMoves = statistics.successfulMoves,
        totalMerges = statistics.totalMerges,
        undoUses = statistics.undoUses,
    ),
)
