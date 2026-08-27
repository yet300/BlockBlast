package ge.yet.game.twentyfortyeight.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import ge.yet.game.twentyfortyeight.engine.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SwipeDetectorTest {
    private val config = SwipeConfig(
        distanceThresholdPx = 40f,
        velocityThresholdPxPerSecond = 650f,
        touchSlopPx = 8f,
    )

    @Test
    fun `dominant axis resolves direction and exact diagonal is horizontal`() {
        assertMove(Offset(41f, 5f), Direction.Right)
        assertMove(Offset(-41f, 5f), Direction.Left)
        assertMove(Offset(5f, 41f), Direction.Down)
        assertMove(Offset(5f, -41f), Direction.Up)
        assertMove(Offset(40f, 40f), Direction.Right)
        assertMove(Offset(-40f, 40f), Direction.Left)
    }

    @Test
    fun `fast motion past touch slop resolves before distance threshold`() {
        assertEquals(
            GestureDecision.GameMove(Direction.Right),
            resolveGesture(
                delta = Offset(9f, 1f),
                velocity = Velocity(700f, 0f),
                cancelled = false,
                enabled = true,
                startRegion = SwipeStartRegion.Gameplay,
                config = config,
            ),
        )
        assertEquals(
            GestureDecision.GameMove(Direction.Up),
            resolveGesture(
                delta = Offset(9f, 1f),
                velocity = Velocity(0f, -700f),
                cancelled = false,
                enabled = true,
                startRegion = SwipeStartRegion.Gameplay,
                config = config,
            ),
        )
    }

    @Test
    fun `subthreshold motion remains a tap`() {
        assertEquals(
            GestureDecision.PendingTap,
            resolveGesture(
                delta = Offset(20f, 3f),
                velocity = Velocity(300f, 0f),
                cancelled = false,
                enabled = true,
                startRegion = SwipeStartRegion.Gameplay,
                config = config,
            ),
        )
        assertEquals(
            GestureDecision.PendingTap,
            resolveGesture(
                delta = Offset(7f, 0f),
                velocity = Velocity(900f, 0f),
                cancelled = false,
                enabled = true,
                startRegion = SwipeStartRegion.Gameplay,
                config = config,
            ),
        )
    }

    @Test
    fun `vertical support gesture delegates while horizontal support gesture moves`() {
        assertEquals(
            GestureDecision.DelegateVerticalScroll,
            resolveGesture(
                delta = Offset(2f, 50f),
                velocity = Velocity.Zero,
                cancelled = false,
                enabled = true,
                startRegion = SwipeStartRegion.VerticalScrollSupport,
                config = config,
            ),
        )
        assertEquals(
            GestureDecision.GameMove(Direction.Left),
            resolveGesture(
                delta = Offset(-50f, 2f),
                velocity = Velocity.Zero,
                cancelled = false,
                enabled = true,
                startRegion = SwipeStartRegion.VerticalScrollSupport,
                config = config,
            ),
        )
    }

    @Test
    fun `cancelled and disabled gestures never move`() {
        assertEquals(
            GestureDecision.Cancelled,
            resolveGesture(
                delta = Offset(100f, 0f),
                velocity = Velocity(1000f, 0f),
                cancelled = true,
                enabled = true,
                startRegion = SwipeStartRegion.Gameplay,
                config = config,
            ),
        )
        assertEquals(
            GestureDecision.Cancelled,
            resolveGesture(
                delta = Offset(100f, 0f),
                velocity = Velocity(1000f, 0f),
                cancelled = false,
                enabled = false,
                startRegion = SwipeStartRegion.Gameplay,
                config = config,
            ),
        )
    }

    @Test
    fun `configuration rejects non finite and non positive thresholds`() {
        assertFailsWith<IllegalArgumentException> { config.copy(distanceThresholdPx = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { config.copy(distanceThresholdPx = 0f) }
        assertFailsWith<IllegalArgumentException> { config.copy(velocityThresholdPxPerSecond = -1f) }
        assertFailsWith<IllegalArgumentException> { config.copy(touchSlopPx = Float.POSITIVE_INFINITY) }
    }

    private fun assertMove(delta: Offset, expected: Direction) {
        assertEquals(
            GestureDecision.GameMove(expected),
            resolveGesture(
                delta = delta,
                velocity = Velocity.Zero,
                cancelled = false,
                enabled = true,
                startRegion = SwipeStartRegion.Gameplay,
                config = config,
            ),
        )
    }
}
