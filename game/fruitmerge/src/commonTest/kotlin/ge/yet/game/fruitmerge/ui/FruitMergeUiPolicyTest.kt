package ge.yet.game.fruitmerge.ui

import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FruitMergeUiPolicyTest {
    @Test
    fun `active shake moves the board while reduced motion stays still`() {
        val active = shakeVisualTransform(FruitMergeEngine.SHAKE_DURATION_STEPS - 1, false)
        val reduced = shakeVisualTransform(FruitMergeEngine.SHAKE_DURATION_STEPS - 1, true)
        val idle = shakeVisualTransform(0, false)

        assertNotEquals(0f, active.translationXDp)
        assertEquals(ShakeVisualTransform(0f, 0f), reduced)
        assertEquals(ShakeVisualTransform(0f, 0f), idle)
    }
}
