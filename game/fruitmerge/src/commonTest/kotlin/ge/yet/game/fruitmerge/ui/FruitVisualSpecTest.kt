package ge.yet.game.fruitmerge.ui

import ge.yet.game.fruitmerge.engine.FruitLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FruitVisualSpecTest {
    @Test
    fun `every level has a distinct silhouette detail and face identity`() {
        val identities = FruitLevel.entries.map { fruitVisualSpec(it).identityKey }

        assertEquals(FruitLevel.entries.size, identities.toSet().size)
        assertNotEquals(
            fruitVisualSpec(FruitLevel.BLUEBERRY).silhouette,
            fruitVisualSpec(FruitLevel.CHERRY).silhouette,
        )
        assertNotEquals(
            fruitVisualSpec(FruitLevel.CHERRY).silhouette,
            fruitVisualSpec(FruitLevel.STRAWBERRY).silhouette,
        )
    }
}
