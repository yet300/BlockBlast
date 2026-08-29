package ge.yet.game.twentyfortyeight.ui.screen

import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionPorts
import ge.yet.game.twentyfortyeight.store.AnnouncementFact
import ge.yet.game.twentyfortyeight.store.FocusTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AccessibilityFlowTest {
    @Test
    fun `announcement and following focus are queued in publication order`() {
        val ports = TwentyFortyEightSessionPorts()

        ports.announce(AnnouncementFact.Move(scoreDelta = 8L, largestMerge = 8L))
        ports.requestFocus(FocusTarget.Victory)

        val announcement = assertIs<TwentyFortyEightSessionComponent.Effect.Announcement>(
            ports.effect.value.effect,
        )
        assertEquals(AnnouncementFact.Move(8L, 8L), announcement.fact)

        ports.consumeEffect(announcement.id)
        val focus = assertIs<TwentyFortyEightSessionComponent.Effect.Focus>(
            ports.effect.value.effect,
        )
        assertEquals(FocusTarget.Victory, focus.target)
    }

    @Test
    fun `stale acknowledgement cannot consume a newer effect`() {
        val ports = TwentyFortyEightSessionPorts()
        ports.requestFocus(FocusTarget.Board)
        val current = requireNotNull(ports.effect.value.effect)

        ports.consumeEffect(current.id + 1L)

        assertEquals(current, ports.effect.value.effect)
    }

    @Test
    fun `pending accessibility effects are bounded and never silently replaced`() {
        val ports = TwentyFortyEightSessionPorts()
        repeat(TwentyFortyEightSessionComponent.MaxPendingEffects) {
            ports.requestFocus(FocusTarget.Board)
        }

        assertFailsWith<IllegalStateException> {
            ports.requestFocus(FocusTarget.Board)
        }
        assertEquals(
            TwentyFortyEightSessionComponent.MaxPendingEffects,
            ports.effect.value.effects.size,
        )
    }
}
