package ge.yet.game.blockblast.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Converts a point measured in window coordinates into viewport-local coordinates. */
internal fun windowToViewport(
    pointInWindow: Offset,
    viewportOriginInWindow: Offset,
): Offset = pointInWindow - viewportOriginInWindow

/** Converts bounds measured in window coordinates into viewport-local coordinates. */
internal fun windowToViewport(
    rectInWindow: Rect,
    viewportOriginInWindow: Offset,
): Rect = rectInWindow.translate(-viewportOriginInWindow)
