package ge.yet.game.blockblast.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowToViewportTest {
    @Test
    fun point_conversion_subtracts_nonzero_viewport_origin() {
        assertEquals(
            Offset(80f, 140f),
            windowToViewport(
                pointInWindow = Offset(100f, 220f),
                viewportOriginInWindow = Offset(20f, 80f),
            ),
        )
    }

    @Test
    fun rect_conversion_subtracts_nonzero_viewport_origin_from_both_corners() {
        assertEquals(
            Rect(left = 80f, top = 140f, right = 180f, bottom = 340f),
            windowToViewport(
                rectInWindow = Rect(left = 100f, top = 220f, right = 200f, bottom = 420f),
                viewportOriginInWindow = Offset(20f, 80f),
            ),
        )
    }

    @Test
    fun effect_origin_uses_the_same_window_to_viewport_contract_as_drag_overlay() {
        assertEquals(
            Offset(200f, 300f),
            windowToViewport(
                pointInWindow = Offset(224f, 396f),
                viewportOriginInWindow = Offset(24f, 96f),
            ),
        )
    }
}
