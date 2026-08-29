package ge.yet.game.twentyfortyeight.ui.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MotionPolicyTest {
    @Test
    fun `normal policy contains the bounded product motion constants`() {
        val policy = MotionPolicy.Normal

        assertEquals(700f, policy.slideStiffness)
        assertEquals(1f, policy.slideDampingRatio)
        assertEquals(70, policy.mergeCompressMs)
        assertEquals(110, policy.mergePulseMs)
        assertEquals(120, policy.spawnMs)
        assertEquals(160, policy.scoreMs)
        assertEquals(220, policy.crownMs)
        assertEquals(80, policy.victoryMaxStaggerMs)
        assertEquals(180, policy.gameOverMs)
        assertEquals(160, policy.undoMs)
        assertEquals(160, policy.transitionDurationMs)
        assertTrue(policy.transitionDurationMs > 0)
        assertTrue(policy.victoryMaxStaggerMs in 0..policy.transitionDurationMs)
    }

    @Test
    fun `reduced policy removes spatial motion and keeps a short finite alpha`() {
        val policy = MotionPolicy.Reduced

        assertFalse(policy.usesSpatialMotion)
        assertTrue(policy.alphaMs in 60..80)
        assertTrue(policy.transitionDurationMs > 0)
        assertSame(policy, motionPolicy(durationScale = 0f))
    }

    @Test
    fun `any nonzero duration scale selects normal policy`() {
        assertSame(MotionPolicy.Normal, motionPolicy(durationScale = 1f))
        assertSame(MotionPolicy.Normal, motionPolicy(durationScale = 0.01f))
        assertSame(MotionPolicy.Normal, motionPolicy(durationScale = -1f))
    }
}
