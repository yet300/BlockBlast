package ge.yet.game.twentyfortyeight.engine

internal object GameRules {
    fun legalDirections(board: RuntimeBoard): Set<Direction> = Direction.entries
        .filterTo(linkedSetOf()) { direction -> canMove(board, direction) }

    private fun canMove(board: RuntimeBoard, direction: Direction): Boolean {
        board.tiles.forEachIndexed { index, tile ->
            if (tile == null) return@forEachIndexed
            val source = Position.fromIndex(index)
            val target = source.next(direction) ?: return@forEachIndexed
            val targetTile = board[target]
            if (
                targetTile == null ||
                targetTile.value == tile.value && tile.value.value <= TileValue.MAX_MERGE_INPUT
            ) {
                return true
            }
        }
        return false
    }
}

private fun Position.next(direction: Direction): Position? {
    val nextRow = when (direction) {
        Direction.Up -> row - 1
        Direction.Down -> row + 1
        Direction.Left, Direction.Right -> row
    }
    val nextColumn = when (direction) {
        Direction.Left -> column - 1
        Direction.Right -> column + 1
        Direction.Up, Direction.Down -> column
    }
    if (nextRow !in 0 until Board.SIZE || nextColumn !in 0 until Board.SIZE) return null
    return Position(nextRow, nextColumn)
}
