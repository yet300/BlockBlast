package ge.yet.game.fruitmerge.ui

import ge.yet.game.fruitmerge.engine.FruitLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
        assertEquals(
            FruitSilhouette.CROWNED,
            fruitVisualSpec(FruitLevel.PINEAPPLE).silhouette,
        )
        assertEquals(
            FruitDetail.DIAMONDS,
            fruitVisualSpec(FruitLevel.PINEAPPLE).detail,
        )
        assertEquals(
            FruitSilhouette.STRIPED,
            fruitVisualSpec(FruitLevel.WATERMELON).silhouette,
        )
        assertEquals(
            FruitDetail.RIND,
            fruitVisualSpec(FruitLevel.WATERMELON).detail,
        )
    }

    @Test
    fun `all fruits share one outline and upper left light with a bounded three tone palette`() {
        val specs = FruitLevel.entries.map(::fruitVisualSpec)

        assertEquals(1, specs.map { it.outline }.distinct().size)
        assertTrue(specs.all { it.lightDirection == FruitLightDirection.UPPER_LEFT })
        assertTrue(specs.all { it.base != it.shadow && it.base != it.highlight && it.shadow != it.highlight })
    }

    @Test
    fun `all fruits have distinct base colors and defined character blush tones`() {
        val specs = FruitLevel.entries.map(::fruitVisualSpec)

        // Every fruit has an identifiable unique base hue
        assertEquals(FruitLevel.entries.size, specs.map { it.base }.distinct().size)
        // Every fruit has defined blush, face ink, and leaf colors
        assertTrue(specs.all { it.blush.alpha > 0f })
        assertTrue(specs.all { it.faceInk.alpha > 0f })
        assertTrue(specs.all { it.leaf.alpha > 0f })
    }
}
