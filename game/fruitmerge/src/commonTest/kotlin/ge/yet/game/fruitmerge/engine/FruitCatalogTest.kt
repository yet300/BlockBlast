package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FruitCatalogTest {
    @Test
    fun `catalog has ten increasing levels and only first five spawn`() {
        assertEquals(10, FruitLevel.entries.size)
        assertTrue(
            FruitLevel.entries.zipWithNext().all { (first, second) ->
                second.radius > first.radius &&
                    second.mass > first.mass &&
                    second.mergeScore > first.mergeScore
            },
        )
        assertEquals(
            setOf(
                FruitLevel.BLUEBERRY,
                FruitLevel.CHERRY,
                FruitLevel.STRAWBERRY,
                FruitLevel.PLUM,
                FruitLevel.MANDARIN,
            ),
            FruitLevel.spawnable.toSet(),
        )
    }
}
