package ge.yet.game.twentyfortyeight.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeMotionTest {
    @Test
    fun `merge source stretches along travel axis then squashes within bounds`() {
        val horizontal = mergeSourceVisual(progress = 0.4f, horizontalTravel = true)
        val vertical = mergeSourceVisual(progress = 0.4f, horizontalTravel = false)

        assertTrue(horizontal.scaleX > horizontal.scaleY)
        assertTrue(vertical.scaleY > vertical.scaleX)
        listOf(horizontal, vertical).forEach { visual ->
            assertTrue(visual.scaleX in 0.84f..1.10f)
            assertTrue(visual.scaleY in 0.84f..1.10f)
            assertTrue(visual.alpha in 0f..1f)
        }
        repeat(101) { step ->
            val progress = step / 100f
            listOf(
                mergeSourceVisual(progress, horizontalTravel = true),
                mergeSourceVisual(progress, horizontalTravel = false),
                mergeResultVisual(progress),
            ).forEach { visual ->
                assertTrue(visual.positionProgress in 0f..1f)
                assertTrue(visual.alpha in 0f..1f)
                assertTrue(visual.scaleX in 0.82f..1.10f)
                assertTrue(visual.scaleY in 0.82f..1.10f)
            }
        }
    }

    @Test
    fun `merge result rises from below one through bounded overshoot and settles`() {
        val entering = mergeResultVisual(0.44f)
        val peak = mergeResultVisual(0.72f)
        val settled = mergeResultVisual(1f)

        assertTrue(entering.scaleX < 1f)
        assertEquals(1.08f, peak.scaleX, absoluteTolerance = 0.001f)
        assertTrue(peak.scaleX <= 1.08f)
        assertEquals(1f, settled.scaleX)
        assertEquals(1f, settled.scaleY)
        assertEquals(1f, settled.alpha)
    }

    @Test
    fun `liquid bridge and halo are bounded and disappear after impact`() {
        val bridge = mergeEffectVisual(progress = 0.38f, enabled = true)
        val impact = mergeEffectVisual(progress = 0.58f, enabled = true)
        val finished = mergeEffectVisual(progress = 1f, enabled = true)

        assertTrue(bridge.bridgeAlpha in 0f..0.44f)
        assertTrue(bridge.bridgeWidthFraction in 0f..0.18f)
        assertTrue(impact.haloAlpha in 0f..0.35f)
        assertTrue(impact.haloRadiusFraction in 0f..0.72f)
        assertEquals(0f, finished.bridgeAlpha)
        assertEquals(0f, finished.haloAlpha)
    }

    @Test
    fun `reduced motion suppresses liquid geometry`() {
        assertEquals(
            MergeEffectVisual.Hidden,
            mergeEffectVisual(progress = 0.5f, enabled = false),
        )
    }
}
