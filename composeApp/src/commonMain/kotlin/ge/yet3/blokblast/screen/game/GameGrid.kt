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
import androidx.compose.ui.platform.LocalDensity
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
private val GAP_DP = 2.dp
private val GRID_PADDING_DP = 6.dp

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
    val density = LocalDensity.current
    val gapPx = with(density) { GAP_DP.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .padding(GRID_PADDING_DP)
            .then(
                if (comboStripes != null) Modifier.comboStripes(comboStripes) else Modifier,
            )
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val widthPx = coords.size.width.toFloat()
                val cellPx = (widthPx - (COLS - 1) * gapPx) / COLS
                onGridMeasured?.invoke(pos.x, pos.y, cellPx, gapPx)
            },
    ) {
        val cellSize: Dp = (maxWidth - (COLS - 1) * GAP_DP) / COLS

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
                            x = col * (cellSize + GAP_DP),
                            y = row * (cellSize + GAP_DP),
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
