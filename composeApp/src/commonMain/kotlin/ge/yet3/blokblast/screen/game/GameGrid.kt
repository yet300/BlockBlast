package ge.yet3.blokblast.screen.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet3.blokblast.screen.game.effects.ComboStripesState
import ge.yet3.blokblast.screen.game.effects.comboStripes
import ge.yet3.blokblast.theme.pieceColor
import ge.yet3.blokblast.theme.pieceColorPreview

private const val COLS = Grid.SIZE // 8

/**
 * Renders the 8×8 game board using [BlockPiece] composables.
 *
 * @param grid            Current board state.
 * @param selectedPiece   Piece selected by tap (null if none).
 * @param dragDropState   Current drag-and-drop state (null if not active).
 * @param onCellTapped    Called with (x, y) when the player taps a cell.
 * @param comboStripes    Optional combo stripe effect state.
 * @param onGridMeasured  Callback with grid origin (window coords), cell size, and gap in px.
 */
@Composable
fun GameGrid(
    grid: Grid,
    selectedPiece: Piece?,
    onCellTapped: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
    dragDropState: DragDropState? = null,
    comboStripes: ComboStripesState? = null,
    onGridMeasured: ((gridOriginX: Float, gridOriginY: Float, cellSizePx: Float, gapPx: Float) -> Unit)? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .padding(6.dp)
            .then(
                if (comboStripes != null) Modifier.comboStripes(comboStripes) else Modifier,
            )
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val widthPx = coords.size.width.toFloat()
                val gapPx = 2f * coords.size.width / (coords.size.width.toFloat()) // ~2dp scaled
                val cellPx = (widthPx - (COLS - 1) * gapPx) / COLS
                onGridMeasured?.invoke(pos.x, pos.y, cellPx, gapPx)
            },
    ) {
        val cellSize: Dp = (maxWidth - (COLS - 1) * 2.dp) / COLS
        val gapDp = 2.dp

        // Compute hover ghost cells from drag state
        val hoverCells: Set<Pair<Int, Int>> = if (dragDropState?.isDragging == true && dragDropState.hoverAnchor != null) {
            val (ax, ay) = dragDropState.hoverAnchor!!
            val piece = dragDropState.draggedPiece!!
            piece.shape.cells.map { (ax + it.x) to (ay + it.y) }.toSet()
        } else {
            emptySet()
        }
        val hoverColorId = dragDropState?.draggedPiece?.colorId
        val hoverValid = dragDropState?.isValidPlacement == true

        for (row in 0 until COLS) {
            for (col in 0 until COLS) {
                val x = col
                val y = row
                val isFilled = !grid.isEmpty(x, y)
                val isHoverGhost = (x to y) in hoverCells

                val cellColor = when {
                    isFilled -> pieceColor(grid.cells[y][x])
                    isHoverGhost && hoverColorId != null -> {
                        val base = pieceColorPreview(hoverColorId)
                        if (hoverValid) base else base.copy(alpha = 0.15f)
                    }
                    selectedPiece != null && previewHit(selectedPiece, x, y, grid) ->
                        pieceColorPreview(selectedPiece.colorId)
                    else -> emptyColor
                }

                BlockPiece(
                    color = cellColor,
                    cellSize = cellSize,
                    filled = isFilled || (isHoverGhost && hoverValid),
                    modifier = Modifier
                        .offset(
                            x = col * (cellSize + gapDp),
                            y = row * (cellSize + gapDp),
                        )
                        .clickable(enabled = selectedPiece != null) {
                            onCellTapped(x, y)
                        },
                )
            }
        }
    }
}

private fun previewHit(piece: Piece, x: Int, y: Int, grid: Grid): Boolean = false
