package ge.yet.game.twentyfortyeight.engine

internal data class Position(
    val row: Int,
    val column: Int,
) {
    init {
        require(row in 0 until Board.SIZE) { "Row must be in 0..${Board.SIZE - 1}: $row" }
        require(column in 0 until Board.SIZE) { "Column must be in 0..${Board.SIZE - 1}: $column" }
    }

    val index: Int = row * Board.SIZE + column

    companion object {
        fun fromIndex(index: Int): Position {
            require(index in 0 until Board.CELL_COUNT) {
                "Position index must be in 0..${Board.CELL_COUNT - 1}: $index"
            }
            return Position(row = index / Board.SIZE, column = index % Board.SIZE)
        }
    }
}

internal class Board private constructor(
    private val cells: List<Long?>,
) {
    val values: List<Long?>
        get() = cells

    operator fun get(position: Position): TileValue? = cells[position.index]?.let(::TileValue)

    fun emptyPositions(): List<Position> = buildList {
        cells.forEachIndexed { index, value ->
            if (value == null) add(Position.fromIndex(index))
        }
    }

    fun maxTile(): TileValue? = cells.filterNotNull().maxOrNull()?.let(::TileValue)

    fun sum(): Long = cells.filterNotNull().fold(0L) { total, value ->
        if (Long.MAX_VALUE - total < value) {
            throw ArithmeticException("2048 board sum overflows Long")
        }
        total + value
    }

    fun neighbors(position: Position): List<Position> = buildList {
        if (position.row > 0) add(Position(position.row - 1, position.column))
        if (position.row < SIZE - 1) add(Position(position.row + 1, position.column))
        if (position.column > 0) add(Position(position.row, position.column - 1))
        if (position.column < SIZE - 1) add(Position(position.row, position.column + 1))
    }

    fun withValue(position: Position, value: TileValue): Board {
        val updated = cells.toMutableList()
        updated[position.index] = value.value
        return Board(updated.toList())
    }

    override fun equals(other: Any?): Boolean = other is Board && cells == other.cells

    override fun hashCode(): Int = cells.hashCode()

    override fun toString(): String = "Board(values=$cells)"

    companion object {
        const val SIZE: Int = 4
        const val CELL_COUNT: Int = SIZE * SIZE
        const val Side: Int = SIZE
        const val CellCount: Int = CELL_COUNT

        fun empty(): Board = Board(List(CELL_COUNT) { null })

        fun fromValues(values: List<Long?>): Board {
            require(values.size == CELL_COUNT) {
                "Board must contain exactly $CELL_COUNT values: ${values.size}"
            }
            values.filterNotNull().forEach(::TileValue)
            return Board(values.toList())
        }
    }
}

internal class RuntimeBoard private constructor(
    val tiles: List<RuntimeTile?>,
) {
    init {
        require(tiles.size == Board.CELL_COUNT) {
            "Runtime board must contain exactly ${Board.CELL_COUNT} tiles: ${tiles.size}"
        }
    }

    fun valueBoard(): Board = Board.fromValues(tiles.map { it?.value?.value })

    companion object {
        fun restore(board: Board): Pair<RuntimeBoard, Long> {
            var nextId = 1L
            val tiles = board.values.map { value ->
                value?.let {
                    RuntimeTile(
                        id = TileId(nextId++),
                        value = TileValue(it),
                    )
                }
            }
            return RuntimeBoard(tiles) to nextId
        }
    }
}
