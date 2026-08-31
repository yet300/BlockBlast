package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FruitCatalogTest {
    @Test
    fun `catalog exposes the market identities in stable merge order`() {
        assertEquals(
            listOf(
                FruitLevel.BLUEBERRY,
                FruitLevel.RASPBERRY,
                FruitLevel.STRAWBERRY,
                FruitLevel.LIME,
                FruitLevel.MANDARIN,
                FruitLevel.APPLE,
                FruitLevel.PEAR,
                FruitLevel.PEACH,
                FruitLevel.PINEAPPLE,
                FruitLevel.WATERMELON,
            ),
            FruitLevel.entries,
        )
    }

    @Test
    fun `legacy fruit names restore to their market identities`() {
        assertEquals(FruitLevel.RASPBERRY, FruitLevel.fromPersistedName("CHERRY"))
        assertEquals(FruitLevel.LIME, FruitLevel.fromPersistedName("PLUM"))
        assertEquals(FruitLevel.WATERMELON, FruitLevel.fromPersistedName("MELON"))
    }

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
                FruitLevel.RASPBERRY,
                FruitLevel.STRAWBERRY,
                FruitLevel.LIME,
                FruitLevel.MANDARIN,
            ),
            FruitLevel.spawnable.toSet(),
        )
    }
}
