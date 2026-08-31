package ge.yet.game.fruitmerge.ui

import ge.yet.game.fruitmerge.engine.FruitLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class FruitVisualSpecTest {
    @Test
    fun `every level has a distinct silhouette detail and face identity`() {
        val identities = FruitLevel.entries.map { fruitVisualSpec(it).identityKey }

        assertEquals(FruitLevel.entries.size, identities.toSet().size)
        assertNotEquals(
            fruitVisualSpec(FruitLevel.BLUEBERRY).silhouette,
            fruitVisualSpec(FruitLevel.RASPBERRY).silhouette,
        )
        assertNotEquals(
            fruitVisualSpec(FruitLevel.RASPBERRY).silhouette,
            fruitVisualSpec(FruitLevel.STRAWBERRY).silhouette,
        )
        assertEquals(
            FruitSilhouette.CLUSTER,
            fruitVisualSpec(FruitLevel.RASPBERRY).silhouette,
        )
        assertEquals(
            FruitDetail.DRUPELETS,
            fruitVisualSpec(FruitLevel.RASPBERRY).detail,
        )
        assertEquals(
            FruitSilhouette.CITRUS,
            fruitVisualSpec(FruitLevel.LIME).silhouette,
        )
        assertEquals(
            FruitDetail.WEDGES,
            fruitVisualSpec(FruitLevel.LIME).detail,
        )
    }

    @Test
    fun `all fruits share one outline and upper left light with a bounded three tone palette`() {
        val specs = FruitLevel.entries.map(::fruitVisualSpec)

        assertEquals(1, specs.map { it.outline }.distinct().size)
        assertTrue(specs.all { it.lightDirection == FruitLightDirection.UPPER_LEFT })
        assertTrue(specs.all { it.base != it.shadow && it.base != it.highlight && it.shadow != it.highlight })
    }
}
