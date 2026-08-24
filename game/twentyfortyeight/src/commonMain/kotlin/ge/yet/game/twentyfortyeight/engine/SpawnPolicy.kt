package ge.yet.game.twentyfortyeight.engine

internal data class SpawnedValue(
    val position: Position,
    val value: TileValue,
    val rngAfter: RngState,
)

internal class SpawnPolicy(
    private val drawInt: (RngState, Int) -> Pair<Int, RngState> = SplitMix64::nextInt,
) {
    fun spawn(board: Board, rng: RngState): SpawnedValue? {
        val emptyPositions = board.emptyPositions()
        if (emptyPositions.isEmpty()) return null

        val (positionIndex, afterPosition) = drawInt(rng, emptyPositions.size)
        require(positionIndex in emptyPositions.indices) {
            "Random position index must be in ${emptyPositions.indices}: $positionIndex"
        }
        val (valueRoll, afterValue) = drawInt(afterPosition, VALUE_ROLL_BOUND)
        require(valueRoll in 0 until VALUE_ROLL_BOUND) {
            "Random value roll must be in 0..${VALUE_ROLL_BOUND - 1}: $valueRoll"
        }
        return SpawnedValue(
            position = emptyPositions[positionIndex],
            value = TileValue(if (valueRoll == FOUR_ROLL) 4L else 2L),
            rngAfter = afterValue,
        )
    }

    fun newBoard(rng: RngState): Pair<Board, RngState> {
        val first = checkNotNull(spawn(Board.empty(), rng))
        val afterFirst = Board.empty().withValue(first.position, first.value)
        val second = checkNotNull(spawn(afterFirst, first.rngAfter))
        return afterFirst.withValue(second.position, second.value) to second.rngAfter
    }

    private companion object {
        const val VALUE_ROLL_BOUND: Int = 10
        const val FOUR_ROLL: Int = 0
    }
}
