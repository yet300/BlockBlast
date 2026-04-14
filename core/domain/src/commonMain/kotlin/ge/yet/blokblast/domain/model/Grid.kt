package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

/**
 * Fixed 8x8 board. `cells[y][x]` stores a colorId or [EMPTY] (-1).
 * Color is purely visual — clearing logic ignores it and only checks emptiness.
 */
@Serializable
data class Grid(
    val cells: List<List<Int>> = List(SIZE) { List(SIZE) { EMPTY } }
) {
    fun isEmpty(x: Int, y: Int): Boolean = cells[y][x] == EMPTY
    fun inBounds(x: Int, y: Int): Boolean = x in 0 until SIZE && y in 0 until SIZE

    fun withCell(x: Int, y: Int, colorId: Int): Grid {
        val newRow = cells[y].toMutableList().also { it[x] = colorId }
        val newCells = cells.toMutableList().also { it[y] = newRow }
        return Grid(newCells)
    }

    fun withCells(positions: List<Position>, colorId: Int): Grid {
        val mutable = cells.map { it.toMutableList() }.toMutableList()
        for (p in positions) mutable[p.y][p.x] = colorId
        return Grid(mutable.map { it.toList() })
    }

    fun clearedAt(positions: Set<Position>): Grid {
        if (positions.isEmpty()) return this
        val mutable = cells.map { it.toMutableList() }.toMutableList()
        for (p in positions) mutable[p.y][p.x] = EMPTY
        return Grid(mutable.map { it.toList() })
    }

    companion object {
        const val SIZE = 8
        const val EMPTY = -1
    }
}
