package ge.yet.game.fruitmerge.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FruitMergeTutorialTest {
    @Test
    fun `tutorial motion respects visibility measured bounds and reduced motion`() {
        assertEquals(TutorialMotionMode.HIDDEN, tutorialMotionMode(false, true, false))
        assertEquals(TutorialMotionMode.HIDDEN, tutorialMotionMode(true, false, false))
        assertEquals(TutorialMotionMode.STATIC, tutorialMotionMode(true, true, true))
        assertEquals(TutorialMotionMode.ANIMATED, tutorialMotionMode(true, true, false))
    }
}
