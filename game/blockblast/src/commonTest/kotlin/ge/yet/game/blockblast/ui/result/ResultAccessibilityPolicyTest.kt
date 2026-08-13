package ge.yet.game.blockblast.ui.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultAccessibilityPolicyTest {

    @Test
    fun continue_phase_discloses_advertisement_in_primary_action_semantics() {
        assertEquals(
            "Continue (5), Advertisement",
            resultPrimaryContentDescription(
                isContinuePhase = true,
                primaryText = "Continue (5)",
                advertisementLabel = "Advertisement",
            ),
        )
    }

    @Test
    fun new_game_phase_keeps_normal_text_semantics() {
        assertNull(
            resultPrimaryContentDescription(
                isContinuePhase = false,
                primaryText = "New game",
                advertisementLabel = "Advertisement",
            ),
        )
    }

    @Test
    fun free_continue_does_not_announce_an_advertisement() {
        assertNull(
            resultPrimaryContentDescription(
                isContinuePhase = true,
                primaryText = "Continue (5)",
                advertisementLabel = null,
            ),
        )
    }
}
