package ge.yet3.blokblast.screen.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.times
import ge.yet.blokblast.domain.model.Piece
import ge.yet3.blokblast.component.modifier.CellOffset
import ge.yet3.blokblast.component.modifier.liftedPieceShadow

/**
 * Floating polyomino that follows the finger while the user is dragging from
 * the tray. Owns:
 *   - the absolute placement (anchored above the finger by [verticalLift])
 *   - the lift transform (scale + alpha)
 *   - the single silhouette shadow (one graphics layer regardless of cell count)
 *
 * Extracted from `GameContent` so the screen file stays readable as more
 * features land. All inputs are values; nothing about the drag pipeline lives
 * here besides rendering.
 */
@Composable
fun DraggedPieceOverlay(
    piece: Piece,
    color: Color,
    cellSize: Dp,
    gap: Dp,
    verticalLift: Dp,
    dragDropState: DragDropState,
    modifier: Modifier = Modifier,
) {
    val shadowCells = remember(piece) {
        piece.shape.cells.map { CellOffset(it.x, it.y) }
    }

    Box(
        modifier = modifier
            .offset {
                val dragPosition = dragDropState.dragPosition
                val ghostW = cellSize.toPx() * piece.shape.width +
                    gap.toPx() * (piece.shape.width - 1).coerceAtLeast(0)
                val ghostH = cellSize.toPx() * piece.shape.height +
                    gap.toPx() * (piece.shape.height - 1).coerceAtLeast(0)
                IntOffset(
                    x = (dragPosition.x - ghostW / 2f).toInt(),
                    y = (dragPosition.y - ghostH - verticalLift.toPx()).toInt(),
                )
            }
            .graphicsLayer {
                scaleX = 1.15f
                scaleY = 1.15f
                alpha = 0.85f
            },
    ) {
        val totalW = piece.shape.width * cellSize + (piece.shape.width - 1) * gap
        val totalH = piece.shape.height * cellSize + (piece.shape.height - 1) * gap
        Box(
            modifier = Modifier
                .size(totalW, totalH)
                .liftedPieceShadow(
                    pieceColor = color,
                    cells = shadowCells,
                    cellSizeDp = cellSize.value,
                    gapDp = gap.value,
                    cornerRadiusDp = 4f,
                    lift = 1f,
                ),
        ) {
            piece.shape.cells.forEach { pos ->
                BlockPiece(
                    color = color,
                    cellSize = cellSize,
                    filled = true,
                    modifier = Modifier.offset(
                        x = pos.x * (cellSize + gap),
                        y = pos.y * (cellSize + gap),
                    ),
                )
            }
        }
    }
}
