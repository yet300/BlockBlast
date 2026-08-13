package ge.yet.game.blockblast.domain.engine

import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Polyomino
import kotlin.random.Random

/** Strategy interface — easy to swap in tests with a deterministic implementation. */
internal interface ShapeGenerator {
    fun nextTray(seed: Long? = null): List<Polyomino>
    fun nextTray(grid: Grid, seed: Long? = null): List<Polyomino> = nextTray(seed)
    fun smallReviveTray(): List<Polyomino>

    companion object {
        fun default(): ShapeGenerator = WeightedShapeGenerator()
    }
}

/**
 * Grid-aware generator. Open boards favor varied medium/large trays, while
 * constrained boards retain compact-piece weighting and always receive a
 * placeable shape when the catalog contains one.
 */
internal class WeightedShapeGenerator : ShapeGenerator {

    private val defaultRandom = Random.Default

    override fun nextTray(seed: Long?): List<Polyomino> = nextTray(Grid(), seed)

    override fun nextTray(grid: Grid, seed: Long?): List<Polyomino> {
        val rnd = seed?.let { Random(it) } ?: defaultRandom
        val occupiedCells = grid.cells.count { it != Grid.EMPTY }
        val tray = if (occupiedCells <= OPEN_BOARD_MAX_OCCUPIED_CELLS) {
            val compactShapes = ShapeCatalog.ALL.filter { it.size <= 2 }.shuffled(rnd)
            val mediumShapes = ShapeCatalog.ALL.filter { it.size in 3..4 }.shuffled(rnd)
            val largeShapes = ShapeCatalog.ALL.filter { it.size >= 5 }.shuffled(rnd)
            when (rnd.nextInt(100)) {
                in 0..19 -> listOf(compactShapes[0], mediumShapes[0], largeShapes[0])
                in 20..59 -> listOf(mediumShapes[0], mediumShapes[1], largeShapes[0])
                else -> listOf(mediumShapes[0], largeShapes[0], largeShapes[1])
            }.shuffled(rnd)
        } else {
            // Denser boards retain the compact-biased recovery distribution.
            val small = ShapeCatalog.SMALL.random(rnd)
            val medium = ShapeCatalog.MEDIUM.random(rnd)
            listOf(small, medium, pickWeighted(rnd, setOf(small.id, medium.id)))
                .shuffled(rnd)
        }

        return ensurePlaceable(tray, grid, rnd)
    }

    private fun ensurePlaceable(
        tray: List<Polyomino>,
        grid: Grid,
        random: Random,
    ): List<Polyomino> {
        if (tray.any { it.canFit(grid) }) return tray

        val retained = tray.drop(1)
        val retainedIds = retained.mapTo(mutableSetOf()) { it.id }
        val placeable = ShapeCatalog.ALL.filter {
            it.id !in retainedIds && it.canFit(grid)
        }
        if (placeable.isEmpty()) return tray
        return listOf(placeable.random(random)) + retained
    }

    override fun smallReviveTray(): List<Polyomino> = listOf(
        ShapeCatalog.SINGLE,
        ShapeCatalog.HORIZONTAL_TWO,
        ShapeCatalog.VERTICAL_TWO,
    )

    private fun pickWeighted(rnd: Random, excludedIds: Set<String>): Polyomino {
        // 30% small, 45% medium, 25% large
        val preferredPool = when (rnd.nextInt(100)) {
            in 0..29 -> ShapeCatalog.SMALL.random(rnd)
            in 30..74 -> ShapeCatalog.MEDIUM.random(rnd)
            else -> ShapeCatalog.LARGE.random(rnd)
        }
        if (preferredPool.id !in excludedIds) return preferredPool
        return ShapeCatalog.ALL.filter { it.id !in excludedIds }.random(rnd)
    }

    private fun Polyomino.canFit(grid: Grid): Boolean {
        for (y in 0 until Grid.SIZE) {
            for (x in 0 until Grid.SIZE) {
                if (cells.all { cell ->
                        val gridX = x + cell.x
                        val gridY = y + cell.y
                        grid.inBounds(gridX, gridY) && grid.isEmpty(gridX, gridY)
                    }
                ) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        const val OPEN_BOARD_MAX_OCCUPIED_CELLS = 24
    }
}
