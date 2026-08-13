package ge.yet.game.blockblast.domain.engine

import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.blockblast.domain.engine.selectVoiceFeedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceFeedbackSelectorTest {

    @Test
    fun all_clear_has_highest_priority() {
        assertEquals(
            FeedbackType.UNBELIEVABLE,
            selectVoiceFeedback(
                linesCount = 4,
                isCrossClear = true,
                isBoardEmpty = true,
                comboLevel = 3,
            ),
        )
    }

    @Test
    fun cross_or_four_lines_is_excellent() {
        assertEquals(
            FeedbackType.EXCELLENT,
            selectVoiceFeedback(2, isCrossClear = true, isBoardEmpty = false, comboLevel = 3),
        )
        assertEquals(
            FeedbackType.EXCELLENT,
            selectVoiceFeedback(4, isCrossClear = false, isBoardEmpty = false, comboLevel = 3),
        )
    }

    @Test
    fun exactly_three_lines_is_great_regardless_of_orientation() {
        assertEquals(
            FeedbackType.GREAT,
            selectVoiceFeedback(3, isCrossClear = false, isBoardEmpty = false, comboLevel = 1),
        )
    }

    @Test
    fun exactly_two_lines_is_good() {
        assertEquals(
            FeedbackType.GOOD,
            selectVoiceFeedback(2, isCrossClear = false, isBoardEmpty = false, comboLevel = 1),
        )
    }

    @Test
    fun amazing_is_emitted_only_when_combo_reaches_exactly_three() {
        assertEquals(
            FeedbackType.AMAZING,
            selectVoiceFeedback(1, isCrossClear = false, isBoardEmpty = false, comboLevel = 3),
        )
        assertNull(
            selectVoiceFeedback(
                1,
                isCrossClear = false,
                isBoardEmpty = false,
                comboLevel = 4
            )
        )
    }

    @Test
    fun move_without_eligible_feedback_is_silent() {
        assertNull(
            selectVoiceFeedback(
                0,
                isCrossClear = false,
                isBoardEmpty = true,
                comboLevel = 3
            )
        )
        assertNull(
            selectVoiceFeedback(
                1,
                isCrossClear = false,
                isBoardEmpty = false,
                comboLevel = 2
            )
        )
    }
}
