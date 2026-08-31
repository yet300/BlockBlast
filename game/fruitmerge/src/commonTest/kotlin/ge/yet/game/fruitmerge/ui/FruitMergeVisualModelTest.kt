package ge.yet.game.fruitmerge.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FruitMergeVisualModelTest {
    @Test
    fun `airborne fruit never shows danger feedback`() {
        val airborne = dangerVisual(
            topY = 0.05f,
            dangerY = 0.10f,
            hasJoinedPile = false,
        )

        assertEquals(DangerVisual(intensity = 0f, crying = false), airborne)
    }

    @Test
    fun `danger feedback grows through warning band and cries at line`() {
        val outside = dangerVisual(topY = 0.20f, dangerY = 0.10f, hasJoinedPile = true)
        val approaching = dangerVisual(topY = 0.14f, dangerY = 0.10f, hasJoinedPile = true)
        val atLine = dangerVisual(topY = 0.10f, dangerY = 0.10f, hasJoinedPile = true)

        assertEquals(0f, outside.intensity)
        assertFalse(outside.crying)
        assertTrue(approaching.intensity in 0.49f..0.51f)
        assertFalse(approaching.crying)
        assertEquals(1f, atLine.intensity)
        assertTrue(atLine.crying)
    }

    @Test
    fun `expression is derived from committed visual inputs by priority`() {
        assertEquals(FruitExpression.RESTING, fruitExpression(0f, 0f, DangerVisual(0f, false), false))
        assertEquals(FruitExpression.FALLING, fruitExpression(0.5f, 0f, DangerVisual(0f, false), false))
        assertEquals(FruitExpression.IMPACT, fruitExpression(0f, 0.8f, DangerVisual(0f, false), false))
        assertEquals(FruitExpression.CRYING, fruitExpression(0f, 0f, DangerVisual(1f, true), false))
        assertEquals(FruitExpression.MERGING, fruitExpression(0f, 0f, DangerVisual(0f, false), true))
    }
}
