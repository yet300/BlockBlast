package ge.yet.game.blockblast.ui.result

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultLayoutPolicyTest {

    @Test
    fun every_layout_tier_preserves_minimum_touch_target_height() {
        val policies = listOf(
            resultLayoutPolicy(widthDp = 390f, heightDp = 844f),
            resultLayoutPolicy(widthDp = 320f, heightDp = 568f),
            resultLayoutPolicy(widthDp = 568f, heightDp = 320f),
            resultLayoutPolicy(widthDp = 568f, heightDp = 288f),
        )

        policies.forEach { policy ->
            assertTrue(
                policy.buttonHeightDp >= MIN_TOUCH_TARGET_HEIGHT_DP,
                "Button height ${policy.buttonHeightDp}dp is below the 48dp touch target",
            )
        }
    }

    @Test
    fun compact_phone_keeps_timed_cta_inside_320_by_568_viewport() {
        val budget = resultLayoutBudget(widthDp = 320f, heightDp = 568f)

        assertTrue(budget.policy.isCompact)
        assertFalse(budget.policy.isUltraCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 568f)
    }

    @Test
    fun compact_phone_keeps_timed_cta_inside_360_by_640_viewport() {
        val budget = resultLayoutBudget(widthDp = 360f, heightDp = 640f)

        assertTrue(budget.policy.isCompact)
        assertFalse(budget.policy.isUltraCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 640f)
    }

    @Test
    fun short_landscape_uses_ultra_compact_layout_with_all_actions_visible() {
        val budget = resultLayoutBudget(widthDp = 568f, heightDp = 320f)

        assertTrue(budget.policy.isCompact)
        assertTrue(budget.policy.isUltraCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 320f)
    }

    @Test
    fun short_landscape_fits_after_safe_insets_reduce_effective_height() {
        val budget = resultLayoutBudget(widthDp = 568f, heightDp = 288f)

        assertTrue(budget.policy.isUltraCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 288f)
    }

    @Test
    fun tall_phone_uses_regular_result_layout() {
        val budget = resultLayoutBudget(widthDp = 390f, heightDp = 844f)

        assertFalse(budget.policy.isCompact)
        assertFalse(budget.policy.isUltraCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 844f)
    }

    private companion object {
        const val MIN_TOUCH_TARGET_HEIGHT_DP = 48f
    }
}
