package ge.yet3.blokblast.screen.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import ge.yet.blokblast.domain.model.ClearEvent
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet3.blokblast.screen.game.effects.CellAnimState
import ge.yet3.blokblast.screen.game.effects.ComboStripesState
import ge.yet3.blokblast.screen.game.effects.ParticleBurstState
import ge.yet3.blokblast.screen.game.effects.comboStripes
import ge.yet3.blokblast.screen.game.effects.gridBorderGlow
import ge.yet3.blokblast.screen.game.effects.particleBurst
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
    particleBurst: ParticleBurstState? = null,
    comboLevel: Int = 0,
    clearedEvent: ClearEvent = ClearEvent(),
    isGameOver: Boolean = false,
    onGridMeasured: ((gridOriginX: Float, gridOriginY: Float, cellSizePx: Float, gapPx: Float) -> Unit)? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val gapPx = with(density) { GAP_DP.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .padding(GRID_PADDING_DP)
            .then(
                if (comboStripes != null) Modifier.comboStripes(comboStripes) else Modifier,
            )
            .then(
                if (particleBurst != null) Modifier.particleBurst(particleBurst) else Modifier,
            )
            .gridBorderGlow(comboLevel)
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

        // Pulsing alpha for the hover preview — gentle "breathing" of the
        // target cells while the user holds the piece overhead.
        val hoverPulse = rememberInfiniteTransition(label = "hoverPulse")
        val hoverPulseAlpha by hoverPulse.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "hoverPulseAlpha",
        )

        var prevGrid by remember { mutableStateOf(grid) }
        var prevNonce by remember { mutableIntStateOf(clearedEvent.nonce) }
        
        LaunchedEffect(grid, clearedEvent) {
            prevGrid = grid
            prevNonce = clearedEvent.nonce
        }

        val saturation = animateFloatAsState(
            targetValue = if (isGameOver) 0.2f else 1f,
            animationSpec = tween(400)
        ).value
        val colorMatrix = ColorMatrix().apply { setToSaturation(saturation) }

        for (row in 0 until COLS) {
            for (col in 0 until COLS) {
                val x = col
                val y = row
                
                val cellAnim = remember { CellAnimState() }
                
                var isClearing by remember { mutableStateOf(false) }
                val cellId = grid.colorAt(x, y)
                var displayColor by remember(cellId) {
                    mutableIntStateOf(cellId)
                }

                // Game-over board collapse: staggered "fall" of every filled
                // cell. Stagger from the bottom-up by row, with a small
                // column-based jitter so it feels physical.
                LaunchedEffect(isGameOver) {
                    if (isGameOver && cellId != -1) {
                        val rowStagger = (COLS - 1 - y) * 35L
                        val colJitter = x * 12L
                        val falls = with(density) { 240.dp.toPx() }
                        cellAnim.fall(delayMs = rowStagger + colJitter, distancePx = falls)
                    }
                }

                // Placement animation detection
                LaunchedEffect(cellId) {
                    val currentId = cellId
                    val prevId = prevGrid.colorAt(x, y)
                    if (currentId != -1 && prevId == -1) {
                        cellAnim.popIn(delayMs = (x + y) * 25L)
                    }
                }

                // Clear animation detection
                LaunchedEffect(clearedEvent) {
                    if (clearedEvent.nonce != prevNonce && clearedEvent.cells.any { it.x == x && it.y == y }) {
                        isClearing = true
                        cellAnim.clear(delayMs = (x + y) * 30L)
                        isClearing = false
                        displayColor = -1
                        cellAnim.reset()
                    }
                }

                if (!isClearing) {
                    displayColor = cellId
                }

                val isFilled = displayColor != -1
                val isHoverGhost = (x to y) in hoverCells

                val cellColor = when {
                    isFilled -> pieceColor(displayColor)
                    isHoverGhost && hoverColorId != null -> {
                        val base = pieceColorPreview(hoverColorId)
                        if (hoverValid) base.copy(alpha = base.alpha * hoverPulseAlpha)
                        else base.copy(alpha = 0.15f)
                    }
                    selectedPiece != null && previewHit(selectedPiece, x, y, grid) ->
                        pieceColorPreview(selectedPiece.colorId)
                    else -> emptyColor
                }

                BlockPiece(
                    color = cellColor,
                    cellSize = cellSize,
                    filled = isFilled || (isHoverGhost && hoverValid),
                    scale = cellAnim.scale.value,
                    alpha = cellAnim.alpha.value,
                    flashAlpha = cellAnim.flashAlpha.value,
                    rotationDeg = cellAnim.rotation.value,
                    translateYPx = cellAnim.translateY.value,
                    modifier = Modifier
                        .offset(
                            x = col * (cellSize + GAP_DP),
                            y = row * (cellSize + GAP_DP),
                        )
                        .graphicsLayer { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        .clickable(enabled = selectedPiece != null) {
                            onCellTapped(x, y)
                        },
                )
            }
        }
    }
}

private fun previewHit(piece: Piece, x: Int, y: Int, grid: Grid): Boolean = false
