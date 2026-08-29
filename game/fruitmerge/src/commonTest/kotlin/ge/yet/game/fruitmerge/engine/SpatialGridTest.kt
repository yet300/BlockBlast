package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
