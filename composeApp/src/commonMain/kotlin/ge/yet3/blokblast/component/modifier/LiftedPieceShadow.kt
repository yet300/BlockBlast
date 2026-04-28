package ge.yet3.blokblast.component.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Geometry of one cell in a polyomino. Coordinates are *cell indices* (not
 * pixels): (0, 0) is the top-left cell of the piece's local box.
 */
data class CellOffset(val x: Int, val y: Int)

/**
 * A [Shape] whose outline is the union of [cells], each rendered as a
 * rounded square of side [cellSizeDp] with [gapDp] separation. Used to
 * cast a single shadow that traces the actual polyomino silhouette
 * instead of the bounding box.
 *
 * Implemented with a generic outline (not a rect/round-rect) because the
 * silhouette is non-convex for L/T/S/Z pieces.
 */
class PolyominoShape(
    private val cells: List<CellOffset>,
    private val cellSizeDp: Float,
    private val gapDp: Float,
    private val cornerRadiusDp: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cellPx = with(density) { cellSizeDp.dp.toPx() }
        val gapPx = with(density) { gapDp.dp.toPx() }
        val cornerPx = with(density) { cornerRadiusDp.dp.toPx() }
        val path = Path()
        val corner = CornerRadius(cornerPx, cornerPx)
        for (cell in cells) {
            val left = cell.x * (cellPx + gapPx)
            val top = cell.y * (cellPx + gapPx)
            path.addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + cellPx, top + cellPx),
                    topLeft = corner,
                    topRight = corner,
                    bottomRight = corner,
                    bottomLeft = corner,
                ),
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * Cast a single tinted shadow that traces the polyomino silhouette. Apply
 * to the *parent* box of the dragged piece; the shadow then follows the
 * actual cell layout (L, T, S, Z, etc.) instead of the bounding rect.
 *
 * Replaces the previous per-cell shadow approach: one shadow modifier total
 * is far cheaper than N (each `Modifier.shadow` allocates a graphics layer)
 * and the silhouette is identical.
 *
 * @param pieceColor base piece color — drives the shadow tint
 * @param cells      cell layout (must match the rendered piece)
 * @param cellSizeDp pixel size of each cell, in Dp (must match render)
 * @param gapDp      gap between cells, in Dp (must match render)
 * @param lift       0f..1f drag height — drives elevation and tint alpha
 */
fun Modifier.liftedPieceShadow(
    pieceColor: Color,
    cells: List<CellOffset>,
    cellSizeDp: Float,
    gapDp: Float,
    cornerRadiusDp: Float = 4f,
    lift: Float = 1f,
): Modifier {
    val clamped = lift.coerceIn(0f, 1f)
    val elevationDp = (4 + (clamped * 24f).roundToInt()).dp
    val tinted = pieceColor.copy(alpha = 0.15f + clamped * 0.25f)
    val shape = PolyominoShape(
        cells = cells,
        cellSizeDp = cellSizeDp,
        gapDp = gapDp,
        cornerRadiusDp = cornerRadiusDp,
    )
    return this.shadow(
        elevation = elevationDp,
        shape = shape,
        clip = false,
        ambientColor = tinted,
        spotColor = tinted,
    )
}

/**
 * Single-cell variant — kept for callers that want the original "rounded
 * rect shadow" behaviour (e.g. score chips, single tiles).
 */
fun Modifier.liftedPieceShadow(
    pieceColor: Color,
    shape: Shape,
    lift: Float,
): Modifier {
    val clamped = lift.coerceIn(0f, 1f)
    val elevationDp = (4 + (clamped * 24f).roundToInt()).dp
    val tinted = pieceColor.copy(alpha = 0.15f + clamped * 0.25f)
    return this.shadow(
        elevation = elevationDp,
        shape = shape,
        clip = false,
        ambientColor = tinted,
        spotColor = tinted,
    )
}
