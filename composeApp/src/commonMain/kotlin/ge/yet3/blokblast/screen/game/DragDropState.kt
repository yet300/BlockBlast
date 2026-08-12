package ge.yet3.blokblast.screen.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.app.common.config.AppConfig
import ge.yet.game.domain.model.Grid
import ge.yet.game.domain.model.Piece
import ge.yet.game.domain.model.Polyomino

enum class DragPresentation {
    Idle,
    Dragging,
    Returning,
}

/**
 * UI-side drag-and-drop state for moving pieces from the tray onto the grid.
 *
 * Tracks:
 * - which piece is being dragged
 * - the current finger position (absolute, in window coordinates)
 * - the grid anchor cell the piece would snap to (if valid)
 * - whether the current anchor is a valid placement
 */
class DragDropState {
    var presentation by mutableStateOf(DragPresentation.Idle)
        private set

    /** The piece currently being dragged, or null. */
    var draggedPiece by mutableStateOf<Piece?>(null)
        private set

    /** Current drag position in window coordinates. */
    var dragPosition by mutableStateOf(Offset.Zero)
        private set

    /** Grid cell (x, y) the piece anchor maps to, or null if off-grid. */
    var hoverAnchor by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** Whether the current hover anchor is a valid placement. */
    var isValidPlacement by mutableStateOf(false)
        private set

    /** Offset from piece origin to finger (set on pickup). */
    var fingerOffset by mutableStateOf(Offset.Zero)
        private set

    /** Center of the source tray slot in window coordinates. */
    var sourcePosition by mutableStateOf(Offset.Zero)
        private set

    /** Position from which an invalid-drop return begins. */
    var returnStartPosition by mutableStateOf(Offset.Zero)
        private set

    /** Finger position where the drag started. */
    private var dragStartFingerPos by mutableStateOf(Offset.Zero)

    val isDragging: Boolean get() = presentation == DragPresentation.Dragging
    val isReturning: Boolean get() = presentation == DragPresentation.Returning

    fun startDrag(
        piece: Piece,
        startPosition: Offset,
        pieceOriginOffset: Offset,
        sourcePosition: Offset,
    ) {
        if (presentation != DragPresentation.Idle) return
        draggedPiece = piece
        dragPosition = startPosition
        dragStartFingerPos = startPosition
        fingerOffset = pieceOriginOffset
        this.sourcePosition = sourcePosition
        returnStartPosition = Offset.Zero
        hoverAnchor = null
        isValidPlacement = false
        presentation = DragPresentation.Dragging
    }

    fun updateDrag(
        position: Offset,
        gridOrigin: Offset,
        cellSizePx: Float,
        gapPx: Float,
        grid: Grid,
        ghostCellSizePx: Float,
        ghostGapPx: Float,
        verticalLiftPx: Float,
    ) {
        if (!isDragging) return
        // Apply sensitivity: the piece moves faster than the finger relative
        // to the pickup point. This allows reaching screen corners with less
        // physical thumb movement.
        val delta = position - dragStartFingerPos
        dragPosition = dragStartFingerPos + delta * AppConfig.DRAG_SENSITIVITY

        val piece = draggedPiece ?: return

        // The floating ghost is drawn with its top-left at
        //   (dragPosition - fingerOffset) - (ghostW/2, ghostH) - (0, verticalLift)
        // so the snap anchor must be computed from that same top-left,
        // otherwise the piece lands below/beside where the user sees it.
        val ghostW = piece.shape.width * ghostCellSizePx +
            (piece.shape.width - 1).coerceAtLeast(0) * ghostGapPx
        val ghostH = piece.shape.height * ghostCellSizePx +
            (piece.shape.height - 1).coerceAtLeast(0) * ghostGapPx

        // The floating ghost is drawn with its center at [dragPosition] (virtual
        // finger) horizontally, and entirely above the finger vertically.
        // This is a "lifted" drag style that ensures the piece is never
        // obscured by the user's thumb.
        val ghostTopLeftX = dragPosition.x - ghostW / 2f
        val ghostTopLeftY = dragPosition.y - ghostH - verticalLiftPx

        // Snap by rounding the ghost's top-left to the nearest grid cell
        // — rounding (not floor) so half-cell overlaps jump to the closer
        // column/row, which matches user expectation.
        val step = cellSizePx + gapPx
        val relX = ghostTopLeftX - gridOrigin.x
        val relY = ghostTopLeftY - gridOrigin.y

        val anchorX = kotlin.math.round(relX / step).toInt()
        val anchorY = kotlin.math.round(relY / step).toInt()

        // Always recompute validity — the grid can change under a stationary
        // finger (line-clear animation finishes mid-drag and frees cells).
        // mutableStateOf's structural equality elides redundant emits, so
        // there's no need to gate the writes manually.
        hoverAnchor = anchorX to anchorY
        isValidPlacement = canPlacePiece(piece.shape, anchorX, anchorY, grid)
    }

    fun beginReturn() {
        if (!isDragging || draggedPiece == null) return
        returnStartPosition = dragPosition
        hoverAnchor = null
        isValidPlacement = false
        fingerOffset = Offset.Zero
        presentation = DragPresentation.Returning
    }

    fun finishReturn() {
        if (!isReturning) return
        clearPresentation()
    }

    fun endDrag() = clearPresentation()

    private fun clearPresentation() {
        draggedPiece = null
        dragPosition = Offset.Zero
        dragStartFingerPos = Offset.Zero
        hoverAnchor = null
        isValidPlacement = false
        fingerOffset = Offset.Zero
        sourcePosition = Offset.Zero
        returnStartPosition = Offset.Zero
        presentation = DragPresentation.Idle
    }
}

@Composable
fun rememberDragDropState(): DragDropState = remember { DragDropState() }

/**
 * UI-side placement validity check — mirrors the domain placement rule.
 */
fun canPlacePiece(shape: Polyomino, x: Int, y: Int, grid: Grid): Boolean {
    for (cell in shape.cells) {
        val gx = x + cell.x
        val gy = y + cell.y
        if (!grid.inBounds(gx, gy)) return false
        if (!grid.isEmpty(gx, gy)) return false
    }
    return true
}
