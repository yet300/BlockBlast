package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpatialGridTest {
    @Test
    fun `maximum board stays below all pairs candidate count`() {
        val bodies = List(MAX_BODIES) { index ->
            FruitBody(
                id = index.toLong() + 1,
                level = FruitLevel.BLUEBERRY,
                position = Vec2(
                    x = 0.05f + (index % 10) * 0.095f,
                    y = 0.20f + (index / 10) * 0.095f,
                ),
            )
        }

        val pairs = SpatialGrid().candidatePairs(bodies)

        assertTrue(pairs.size < bodies.size * 12)
        assertEquals(pairs.distinct(), pairs)
    }

    @Test
    fun `candidate pair objects are pooled across fixed steps`() {
        val bodies = listOf(
            FruitBody(1, FruitLevel.MELON, Vec2(0.4f, 0.7f)),
            FruitBody(2, FruitLevel.MELON, Vec2(0.6f, 0.7f)),
        )
        val grid = SpatialGrid()
        val first = grid.candidatePairs(bodies).single()
        val second = grid.candidatePairs(bodies).single()

        assertSame(first, second)
    }
}
