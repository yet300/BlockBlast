package ge.yet3.blokblast.screen.game

import androidx.compose.ui.MotionDurationScale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameMotionPolicyTest {

    @Test
    fun idle_game_has_no_ambient_animation() {
        val policy = gameMotionPolicy(
            comboLevel = 0,
            hasDragHoverTarget = false,
            hasPrediction = false,
            reducedMotion = false,
        )

        assertFalse(policy.animateHoverPulse)
        assertFalse(policy.animatePredictionPulse)
        assertFalse(policy.animateBorderGlow)
        assertTrue(policy.spatialMotionEnabled)
    }

    @Test
    fun active_interactions_enable_only_their_matching_animation() {
        val dragPolicy = gameMotionPolicy(
            comboLevel = 0,
            hasDragHoverTarget = true,
            hasPrediction = false,
            reducedMotion = false,
        )
        val predictionPolicy = gameMotionPolicy(
            comboLevel = 0,
            hasDragHoverTarget = false,
            hasPrediction = true,
            reducedMotion = false,
        )
        val comboPolicy = gameMotionPolicy(
            comboLevel = 1,
            hasDragHoverTarget = false,
            hasPrediction = false,
            reducedMotion = false,
        )

        assertTrue(dragPolicy.animateHoverPulse)
        assertFalse(dragPolicy.animatePredictionPulse)
        assertFalse(dragPolicy.animateBorderGlow)

        assertFalse(predictionPolicy.animateHoverPulse)
        assertTrue(predictionPolicy.animatePredictionPulse)
        assertFalse(predictionPolicy.animateBorderGlow)

        assertFalse(comboPolicy.animateHoverPulse)
        assertFalse(comboPolicy.animatePredictionPulse)
        assertTrue(comboPolicy.animateBorderGlow)
    }

    @Test
    fun non_positive_combo_does_not_animate_border() {
        val policy = gameMotionPolicy(
            comboLevel = -1,
            hasDragHoverTarget = false,
            hasPrediction = false,
            reducedMotion = false,
        )

        assertFalse(policy.animateBorderGlow)
    }

    @Test
    fun reduced_motion_disables_spatial_and_repeating_animation() {
        val policy = gameMotionPolicy(
            comboLevel = 3,
            hasDragHoverTarget = true,
            hasPrediction = true,
            reducedMotion = true,
        )

        assertFalse(policy.animateHoverPulse)
        assertFalse(policy.animatePredictionPulse)
        assertFalse(policy.animateBorderGlow)
        assertFalse(policy.spatialMotionEnabled)
    }

    @Test
    fun zero_duration_scale_is_reduced_motion() {
        assertTrue(durationScale(0f).isReducedMotion())
        assertFalse(durationScale(1f).isReducedMotion())
        assertFalse(null.isReducedMotion())
    }

    private fun durationScale(scaleFactor: Float): MotionDurationScale =
        object : MotionDurationScale {
            override val scaleFactor: Float = scaleFactor
        }
}
