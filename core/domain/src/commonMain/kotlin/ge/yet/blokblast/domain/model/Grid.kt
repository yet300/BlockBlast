package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

/**
 * Fixed 8x8 board backed by a flat [IntArray] of length SIZE*SIZE.
 * Each cell holds a colorId or [EMPTY] (-1). Color is purely visual — clearing
 * logic ignores it and only checks emptiness.
 *
 * The flat-array representation is deliberate: a previous `List<List<Int>>`
 * backing allocated 9 lists (and boxed every Int on the JVM) per mutation —
 * dominant GC pressure during drag/drop + autosave. [IntArray] with [copyOf]
 * reduces per-move allocations by ~50× and removes Integer boxing entirely.
 */
@Serializable
data class Grid(
    val cells: IntArray = IntArray(SIZE * SIZE) { EMPTY },
) {
    /** Linear index for ([x], [y]) — inlined so hot loops avoid the call. */
    inline fun index(x: Int, y: Int): Int = y * SIZE + x

    inline fun colorAt(x: Int, y: Int): Int = cells[index(x, y)]
    inline fun isEmpty(x: Int, y: Int): Boolean = cells[index(x, y)] == EMPTY
    inline fun inBounds(x: Int, y: Int): Boolean = x in 0 until SIZE && y in 0 until SIZE

    fun withCell(x: Int, y: Int, colorId: Int): Grid {
        val c = cells.copyOf()
        c[index(x, y)] = colorId
        return Grid(c)
    }

    fun withCells(positions: List<Position>, colorId: Int): Grid {
        val c = cells.copyOf()
        for (p in positions) c[index(p.x, p.y)] = colorId
        return Grid(c)
    }

    fun clearedAt(positions: Set<Position>): Grid {
        if (positions.isEmpty()) return this
        val c = cells.copyOf()
        for (p in positions) c[index(p.x, p.y)] = EMPTY
        return Grid(c)
    }

    /** True when every cell equals [EMPTY]. O(n) with no allocations. */
    fun isBoardEmpty(): Boolean {
        for (v in cells) if (v != EMPTY) return false
        return true
    }

    // IntArray uses identity equality by default — override so `data class`
    // structural equality (used by StateFlow distinctness + Compose stability)
    // actually compares contents.
    override fun equals(other: Any?): Boolean =
        this === other || (other is Grid && cells.contentEquals(other.cells))

    override fun hashCode(): Int = cells.contentHashCode()

    companion object {
        const val SIZE = 8
        const val EMPTY = -1
    }
}
