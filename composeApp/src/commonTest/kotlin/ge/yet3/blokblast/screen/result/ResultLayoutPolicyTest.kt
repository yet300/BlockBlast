package ge.yet3.blokblast.screen.result

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultLayoutPolicyTest {

    @Test
    fun compact_phone_keeps_timed_cta_inside_320_by_568_viewport() {
        val budget = resultLayoutBudget(widthDp = 320f, heightDp = 568f)

        assertTrue(budget.policy.isCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 568f)
    }

    @Test
    fun compact_phone_keeps_timed_cta_inside_360_by_640_viewport() {
        val budget = resultLayoutBudget(widthDp = 360f, heightDp = 640f)

        assertTrue(budget.policy.isCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 640f)
    }

    @Test
    fun tall_phone_uses_regular_result_layout() {
        val budget = resultLayoutBudget(widthDp = 390f, heightDp = 844f)

        assertFalse(budget.policy.isCompact)
        assertTrue(budget.fixedContentFits)
        assertTrue(budget.boardSizeDp > 0f)
        assertTrue(budget.contentHeightDp <= 844f)
    }
}
