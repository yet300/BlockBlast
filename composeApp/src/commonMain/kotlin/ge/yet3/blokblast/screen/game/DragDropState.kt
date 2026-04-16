package ge.yet3.blokblast.screen.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino

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

    val isDragging: Boolean get() = draggedPiece != null

    fun startDrag(piece: Piece, startPosition: Offset, pieceOriginOffset: Offset) {
        draggedPiece = piece
        dragPosition = startPosition
        fingerOffset = pieceOriginOffset
        hoverAnchor = null
        isValidPlacement = false
    }

    fun updateDrag(
        position: Offset,
        gridOrigin: Offset,
        cellSizePx: Float,
        gapPx: Float,
        grid: Grid,
    ) {
        dragPosition = position
        val piece = draggedPiece ?: return

        // Map finger position to grid cell
        val relX = position.x - fingerOffset.x - gridOrigin.x
        val relY = position.y - fingerOffset.y - gridOrigin.y
        val step = cellSizePx + gapPx

        val anchorX = (relX / step).toInt()
        val anchorY = (relY / step).toInt()

        hoverAnchor = anchorX to anchorY
        isValidPlacement = canPlacePiece(piece.shape, anchorX, anchorY, grid)
    }

    fun endDrag() {
        draggedPiece = null
        dragPosition = Offset.Zero
        hoverAnchor = null
        isValidPlacement = false
        fingerOffset = Offset.Zero
    }
}

@Composable
fun rememberDragDropState(): DragDropState = remember { DragDropState() }

/**
 * UI-side placement validity check — mirrors [GameEngine.canPlace].
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
