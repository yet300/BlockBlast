package ge.yet.game.twentyfortyeight.engine

internal class MoveEngine(
    private val spawnPolicy: SpawnPolicy,
) {
    fun apply(
        input: MoveInput,
        direction: Direction,
        transitionId: Long,
    ): MoveResult {
        val reducedBoard = reduceBoard(
            board = input.board,
            direction = direction,
            firstMergeId = input.nextTileId,
        ) ?: return MoveResult.Failed(direction, MoveFailure.ScoreOverflow)

        if (reducedBoard.board.values() == input.board.values()) {
            return MoveResult.Unchanged(
                direction = direction,
                board = input.board,
                score = input.score,
                rng = input.rng,
            )
        }

        val scoreAfter = checkedAdd(input.score, reducedBoard.scoreDelta)
            ?: return MoveResult.Failed(direction, MoveFailure.ScoreOverflow)
        val spawnIdValue = checkedAdd(input.nextTileId, reducedBoard.merges.size.toLong())
            ?: return MoveResult.Failed(direction, MoveFailure.ScoreOverflow)
        val spawnResult = checkNotNull(spawnPolicy.spawn(reducedBoard.board.valueBoard(), input.rng)) {
            "A changed 2048 move must leave a free cell for its spawn"
        }
        val spawn = SpawnedTile(
            id = TileId(spawnIdValue),
            position = spawnResult.position,
            value = spawnResult.value,
        )
        val finalBoard = reducedBoard.board.withTile(
            position = spawn.position,
            tile = RuntimeTile(spawn.id, spawn.value),
        )
        return MoveResult.Changed(
            transitionId = transitionId,
            direction = direction,
            beforeBoard = input.board,
            afterMoveBoard = reducedBoard.board,
            finalBoard = finalBoard,
            motions = reducedBoard.motions,
            merges = reducedBoard.merges,
            scoreBefore = input.score,
            scoreDelta = reducedBoard.scoreDelta,
            scoreAfter = scoreAfter,
            spawn = spawn,
            rngBefore = input.rng,
            rngAfter = spawnResult.rngAfter,
            victory = if (
                !input.victoryAlreadyReached &&
                reducedBoard.merges.any { it.resultValue.value == VICTORY_VALUE }
            ) {
                VictoryTransition.FirstReached
            } else {
                VictoryTransition.None
            },
            gameOver = if (GameRules.legalDirections(finalBoard).isEmpty()) {
                GameOverTransition.Entered
            } else {
                GameOverTransition.None
            },
        )
    }

    private companion object {
        const val VICTORY_VALUE: Long = 2048L
    }
}

internal fun reduceLineForTest(values: List<Long?>): LineReduction {
    require(values.size == Board.SIZE)
    values.filterNotNull().forEach(::TileValue)
    val compact = values.filterNotNull()
    val output = ArrayList<Long?>(Board.SIZE)
    var score = 0L
    var index = 0
    while (index < compact.size) {
        val current = compact[index]
        val next = compact.getOrNull(index + 1)
        if (next == current && current <= TileValue.MAX_MERGE_INPUT) {
            val merged = current * 2L
            output += merged
            score = checkNotNull(checkedAdd(score, merged)) { "Line merge score overflows Long" }
            index += 2
        } else {
            output += current
            index += 1
        }
    }
    while (output.size < Board.SIZE) output += null
    return LineReduction(output, sourceIds = emptyList(), scoreDelta = score)
}

internal fun checkedAdd(left: Long, right: Long): Long? {
    if (left < 0L || right < 0L || Long.MAX_VALUE - left < right) return null
    return left + right
}

private data class SourceTile(
    val position: Position,
    val tile: RuntimeTile,
)

private data class BoardReduction(
    val board: RuntimeBoard,
    val motions: List<TileMotion>,
    val merges: List<MergeGroup>,
    val scoreDelta: Long,
)

private fun reduceBoard(
    board: RuntimeBoard,
    direction: Direction,
    firstMergeId: Long,
): BoardReduction? {
    val output = MutableList<RuntimeTile?>(Board.CELL_COUNT) { null }
    val motions = mutableListOf<TileMotion>()
    val merges = mutableListOf<MergeGroup>()
    var scoreDelta = 0L
    var nextMergeId = firstMergeId

    repeat(Board.SIZE) { lineIndex ->
        val positions = orientedLine(direction, lineIndex)
        val sources = positions.mapNotNull { position ->
            board[position]?.let { SourceTile(position, it) }
        }
        var sourceIndex = 0
        var outputIndex = 0
        while (sourceIndex < sources.size) {
            val current = sources[sourceIndex]
            val next = sources.getOrNull(sourceIndex + 1)
            val target = positions[outputIndex]
            if (
                next?.tile?.value == current.tile.value &&
                current.tile.value.value <= TileValue.MAX_MERGE_INPUT
            ) {
                val resultId = TileId(nextMergeId)
                val resultValue = TileValue(current.tile.value.value * 2L)
                output[target.index] = RuntimeTile(resultId, resultValue)
                motions += TileMotion(current.tile.id, current.position, target, resultId)
                motions += TileMotion(next.tile.id, next.position, target, resultId)
                merges += MergeGroup(
                    sourceIds = listOf(current.tile.id, next.tile.id),
                    target = target,
                    resultId = resultId,
                    resultValue = resultValue,
                )
                scoreDelta = checkedAdd(scoreDelta, resultValue.value) ?: return null
                nextMergeId = checkedAdd(nextMergeId, 1L) ?: return null
                sourceIndex += 2
            } else {
                output[target.index] = current.tile
                motions += TileMotion(
                    sourceId = current.tile.id,
                    source = current.position,
                    target = target,
                    outcomeId = current.tile.id,
                )
                sourceIndex += 1
            }
            outputIndex += 1
        }
    }
    return BoardReduction(
        board = RuntimeBoard.fromTiles(output),
        motions = motions,
        merges = merges,
        scoreDelta = scoreDelta,
    )
}

private fun orientedLine(direction: Direction, lineIndex: Int): List<Position> = when (direction) {
    Direction.Left -> (0 until Board.SIZE).map { column -> Position(lineIndex, column) }
    Direction.Right -> (Board.SIZE - 1 downTo 0).map { column -> Position(lineIndex, column) }
    Direction.Up -> (0 until Board.SIZE).map { row -> Position(row, lineIndex) }
    Direction.Down -> (Board.SIZE - 1 downTo 0).map { row -> Position(row, lineIndex) }
}
