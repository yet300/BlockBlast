package ge.yet.game.blockblast.ui.result

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultMotionPolicyTest {

    @Test
    fun ambient_motion_is_enabled_when_reduced_motion_is_off() {
        assertTrue(resultAmbientMotionEnabled(reducedMotion = false))
    }

    @Test
    fun ambient_motion_is_disabled_when_reduced_motion_is_on() {
        assertFalse(resultAmbientMotionEnabled(reducedMotion = true))
    }
}
