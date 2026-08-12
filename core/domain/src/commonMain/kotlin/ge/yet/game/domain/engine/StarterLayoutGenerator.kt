package ge.yet.game.domain.engine

import ge.yet.game.domain.model.Grid
import ge.yet.game.domain.model.Polyomino
import ge.yet.game.domain.model.Position
import kotlin.random.Random

internal data class StartingRound(
    val grid: Grid,
    val shapes: List<Polyomino>,
    val starterLayout: StarterLayoutMetadata? = null,
)

internal data class StarterLayoutMetadata(
    val templateId: Int,
    val quarterTurns: Int,
    val reflectedHorizontally: Boolean,
)

/** Builds optional, seeded opening boards and rejects layouts that trap the initial tray. */
internal class StarterLayoutGenerator(
    private val shapeGenerator: ShapeGenerator,
) {
    fun generate(seed: Long?, enabled: Boolean): StartingRound {
        val emptyGrid = Grid()
        if (!enabled) return StartingRound(emptyGrid, shapeGenerator.nextTray(emptyGrid, seed))

        val random = seed?.let(::Random) ?: Random.Default
        if (random.nextInt(100) >= STARTER_CHANCE_PERCENT) {
            return StartingRound(emptyGrid, shapeGenerator.nextTray(emptyGrid, seed))
        }

        repeat(MAX_LAYOUT_ATTEMPTS) {
            val templateIndex = random.nextInt(TEMPLATES.size)
            val quarterTurns = random.nextInt(4)
            val reflectedHorizontally = random.nextBoolean()
            val template = TEMPLATES[templateIndex]
            val transformed = transform(
                positions = template,
                rotations = quarterTurns,
                reflectHorizontally = reflectedHorizontally,
            )
            val grid = transformed.toGrid(random)
            if (grid.hasCompleteLine()) return@repeat

            val shapes = shapeGenerator.nextTray(grid, seed)
            if (shapes.any { !it.canFit(grid) }) return@repeat
            if (canPlaceAll(grid, shapes)) {
                return StartingRound(
                    grid = grid,
                    shapes = shapes,
                    starterLayout = StarterLayoutMetadata(
                        templateId = templateIndex + 1,
                        quarterTurns = quarterTurns,
                        reflectedHorizontally = reflectedHorizontally,
                    ),
                )
            }
        }

        return StartingRound(emptyGrid, shapeGenerator.nextTray(emptyGrid, seed))
    }

    private fun transform(
        positions: Set<Position>,
        rotations: Int,
        reflectHorizontally: Boolean,
    ): Set<Position> = positions.mapTo(mutableSetOf()) { original ->
        var transformed = original
        repeat(rotations) {
            transformed = Position(Grid.SIZE - 1 - transformed.y, transformed.x)
        }
        if (reflectHorizontally) {
            transformed = Position(Grid.SIZE - 1 - transformed.x, transformed.y)
        }
        transformed
    }

    private fun Set<Position>.toGrid(random: Random): Grid {
        val cells = IntArray(Grid.SIZE * Grid.SIZE) { Grid.EMPTY }
        forEachIndexed { index, position ->
            cells[position.y * Grid.SIZE + position.x] = 1 + (index + random.nextInt(6)) % 6
        }
        return Grid(cells)
    }

    private fun canPlaceAll(initialGrid: Grid, shapes: List<Polyomino>): Boolean {
        var expandedStates = 0

        fun search(grid: Grid, remaining: List<Polyomino>): Boolean {
            if (remaining.isEmpty()) return true
            if (++expandedStates > MAX_EXPANDED_STATES) return false

            for (shapeIndex in remaining.indices) {
                val shape = remaining[shapeIndex]
                for (y in 0 until Grid.SIZE) {
                    for (x in 0 until Grid.SIZE) {
                        if (!canPlace(shape, x, y, grid)) continue
                        val nextGrid = placeAndClear(shape, x, y, grid)
                        val nextShapes = remaining.filterIndexed { index, _ -> index != shapeIndex }
                        if (search(nextGrid, nextShapes)) return true
                    }
                }
            }
            return false
        }

        return shapes.size == 3 && search(initialGrid, shapes)
    }

    private fun canPlace(shape: Polyomino, originX: Int, originY: Int, grid: Grid): Boolean =
        shape.cells.all { cell ->
            val x = originX + cell.x
            val y = originY + cell.y
            grid.inBounds(x, y) && grid.isEmpty(x, y)
        }

    private fun Polyomino.canFit(grid: Grid): Boolean {
        for (y in 0 until Grid.SIZE) {
            for (x in 0 until Grid.SIZE) {
                if (canPlace(this, x, y, grid)) return true
            }
        }
        return false
    }

    private fun placeAndClear(
        shape: Polyomino,
        originX: Int,
        originY: Int,
        grid: Grid,
    ): Grid {
        val placed = shape.cells.map { Position(originX + it.x, originY + it.y) }
        val stamped = grid.withCells(placed, colorId = 1)
        val cleared = buildSet {
            for (y in 0 until Grid.SIZE) {
                if ((0 until Grid.SIZE).all { x -> !stamped.isEmpty(x, y) }) {
                    for (x in 0 until Grid.SIZE) add(Position(x, y))
                }
            }
            for (x in 0 until Grid.SIZE) {
                if ((0 until Grid.SIZE).all { y -> !stamped.isEmpty(x, y) }) {
                    for (y in 0 until Grid.SIZE) add(Position(x, y))
                }
            }
        }
        return stamped.clearedAt(cleared)
    }

    private fun Grid.hasCompleteLine(): Boolean =
        (0 until Grid.SIZE).any { y -> (0 until Grid.SIZE).all { x -> !isEmpty(x, y) } } ||
            (0 until Grid.SIZE).any { x -> (0 until Grid.SIZE).all { y -> !isEmpty(x, y) } }

    private companion object {
        const val STARTER_CHANCE_PERCENT = 50
        const val MAX_LAYOUT_ATTEMPTS = 12
        const val MAX_EXPANDED_STATES = 25_000

        val TEMPLATES: List<Set<Position>> = listOf(
            template("##......", "##......", "...###..", "...###..", "......##", "......##", "..##....", "..##...."),
            template("..###...", "..###...", "........", "##....##", "##....##", "........", "...##...", "...##..."),
            template("###.....", "#.......", "#..##...", "...##...", ".....###", ".......#", "..###..#", "..###..."),
            template("....##..", ".##.##..", ".##.....", "......##", "..###.##", "..###...", "........", "##......"),
            template("#.#.#.#.", "........", ".#.#.#.#", "........", "#.#.#.#.", "........", ".#.#.#.#", "........"),
            template("##..##..", "##..##..", "........", "..##..##", "..##..##", "........", "...##...", "........"),
            template("####....", "........", "....####", "........", ".###....", ".....###", "........", "..####.."),
            template("#......#", ".#....#.", "..#..#..", "...##...", "...##...", "..#..#..", ".#....#.", "#......#"),
            template("###..###", "........", ".##.....", ".##.....", ".....##.", ".....##.", "........", "..####.."),
            template("##......", "##..###.", "....###.", "........", ".###....", ".###..##", "......##", "........"),
            template("..#..#..", ".###.###", "........", "##......", "##..##..", "....##..", "..####..", "........"),
            template("....#...", "...###..", "....#...", "##....##", "##....##", "..###...", "...#....", "...#...."),
        )

        fun template(vararg rows: String): Set<Position> = buildSet {
            require(rows.size == Grid.SIZE)
            rows.forEachIndexed { y, row ->
                require(row.length == Grid.SIZE)
                row.forEachIndexed { x, cell -> if (cell == '#') add(Position(x, y)) }
            }
            require(size in 14..22)
        }
    }
}
