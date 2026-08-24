package ge.yet.game.twentyfortyeight.engine

internal sealed interface MoveResult {
    val direction: Direction

    data class Unchanged(
        override val direction: Direction,
        val board: RuntimeBoard,
        val score: Long,
        val rng: RngState,
    ) : MoveResult

    data class Changed(
        val transitionId: Long,
        override val direction: Direction,
        val beforeBoard: RuntimeBoard,
        val afterMoveBoard: RuntimeBoard,
        val finalBoard: RuntimeBoard,
        val motions: List<TileMotion>,
        val merges: List<MergeGroup>,
        val scoreBefore: Long,
        val scoreDelta: Long,
        val scoreAfter: Long,
        val spawn: SpawnedTile,
        val rngBefore: RngState,
        val rngAfter: RngState,
        val victory: VictoryTransition,
        val gameOver: GameOverTransition,
    ) : MoveResult

    data class Failed(
        override val direction: Direction,
        val reason: MoveFailure,
    ) : MoveResult
}

internal enum class MoveFailure {
    ScoreOverflow,
}

internal data class TileMotion(
    val sourceId: TileId,
    val source: Position,
    val target: Position,
    val outcomeId: TileId,
)

internal data class MergeGroup(
    val sourceIds: List<TileId>,
    val target: Position,
    val resultId: TileId,
    val resultValue: TileValue,
)

internal data class SpawnedTile(
    val id: TileId,
    val position: Position,
    val value: TileValue,
)

internal data class MoveInput(
    val board: RuntimeBoard,
    val score: Long,
    val rng: RngState,
    val nextTileId: Long,
    val victoryAlreadyReached: Boolean = false,
) {
    init {
        require(score >= 0L) { "Score must be non-negative: $score" }
        require(nextTileId > 0L) { "Next tile ID must be positive: $nextTileId" }
    }
}

internal data class LineReduction(
    val values: List<Long?>,
    val sourceIds: List<Long>,
    val scoreDelta: Long,
)

internal enum class VictoryTransition {
    None,
    FirstReached,
}

internal enum class GameOverTransition {
    None,
    Entered,
}
