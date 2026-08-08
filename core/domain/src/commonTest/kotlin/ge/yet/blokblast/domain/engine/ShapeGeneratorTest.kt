package ge.yet.blokblast.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapeGeneratorTest {

    private val gen = WeightedShapeGenerator()

    @Test
    fun nextTray_has_size_three() {
        repeat(20) { seed ->
            assertEquals(3, gen.nextTray(seed.toLong()).size)
        }
    }

    @Test
    fun nextTray_deterministic_for_same_seed() {
        val a = gen.nextTray(seed = 42L).map { it.id }
        val b = gen.nextTray(seed = 42L).map { it.id }
        assertEquals(a, b)
    }

    @Test
    fun nextTray_contains_one_small_and_one_medium_at_minimum() {
        // First two slots before shuffle are always SMALL and MEDIUM; after
        // shuffle they're still in the tray. So the tray's id-set always
        // intersects SMALL and MEDIUM.
        repeat(50) { seed ->
            val ids = gen.nextTray(seed.toLong()).map { it.id }.toSet()
            val smallIds = ShapeCatalog.SMALL.map { it.id }.toSet()
            val mediumIds = ShapeCatalog.MEDIUM.map { it.id }.toSet()
            assertTrue(
                ids.any { it in smallIds },
                "tray $ids has no SMALL piece (seed=$seed)",
            )
            assertTrue(
                ids.any { it in mediumIds },
                "tray $ids has no MEDIUM piece (seed=$seed)",
            )
        }
    }

    @Test
    fun smallReviveTray_is_single_horizontal_two_and_vertical_two() {
        val tray = gen.smallReviveTray()

        assertEquals(listOf("single", "h2", "v2"), tray.map { it.id })
        assertEquals(listOf(1, 2, 2), tray.map { it.size })
    }

    @Test
    fun smallReviveTray_is_stable() {
        assertEquals(
            gen.smallReviveTray().map { it.id },
            gen.smallReviveTray().map { it.id },
        )
    }

    @Test
    fun nextTray_all_ids_are_from_catalog() {
        val allIds = ShapeCatalog.ALL.map { it.id }.toSet()
        repeat(100) { seed ->
            for (piece in gen.nextTray(seed.toLong())) {
                assertTrue(piece.id in allIds, "unknown id ${piece.id}")
            }
        }
    }
}
