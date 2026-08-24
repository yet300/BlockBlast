package ge.yet.game.blockblast.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import ge.yet.game.blockblast.domain.model.ClearEvent
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Piece
import ge.yet.game.blockblast.ui.game.effects.CellAnimState
import ge.yet.game.blockblast.ui.game.effects.ComboStripesState
import ge.yet.game.blockblast.ui.game.effects.ParticleBurstState
import ge.yet.game.blockblast.ui.game.effects.comboStripes
import ge.yet.game.blockblast.ui.game.effects.dangerVignette
import ge.yet.game.blockblast.ui.game.effects.gridBorderGlow
import ge.yet.game.blockblast.ui.game.effects.particleBurst
import ge.yet.game.uikit.theme.pieceColor
import ge.yet.game.uikit.theme.pieceColorPreview

private const val COLS = Grid.SIZE // 8
private val GAP_DP = 2.dp
private val GRID_PADDING_DP = 6.dp

@Composable
fun GameGrid(
    grid: Grid,
    selectedPiece: Piece?,
    onCellTapped: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    dragDropState: DragDropState? = null,
    comboStripes: ComboStripesState? = null,
    particleBurst: ParticleBurstState? = null,
    comboLevel: Int = 0,
    reducedMotion: Boolean = false,
    clearedEvent: ClearEvent = ClearEvent(),
    isGameOver: Boolean = false,
    onGridMeasured: ((gridOriginInWindowX: Float, gridOriginInWindowY: Float, cellSizePx: Float, gapPx: Float) -> Unit)? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val gapPx = with(density) { GAP_DP.toPx() }
    val borderMotion = gameMotionPolicy(
        comboLevel = comboLevel,
        hasDragHoverTarget = false,
        hasPrediction = false,
        reducedMotion = reducedMotion,
    )

    // Danger ramp — fraction of the board filled.
    val dangerLevel = remember(grid) {
        var n = 0
        for (v in grid.cells) if (v != Grid.EMPTY) n++
        n.toFloat() / (COLS * COLS).toFloat()
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .padding(GRID_PADDING_DP)
            .then(if (comboStripes != null) Modifier.comboStripes(comboStripes) else Modifier)
            .then(if (particleBurst != null) Modifier.particleBurst(particleBurst) else Modifier)
            .gridBorderGlow(comboLevel, animate = borderMotion.animateBorderGlow)
            .dangerVignette(dangerLevel)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val widthPx = coords.size.width.toFloat()
                val cellPx = (widthPx - (COLS - 1) * gapPx) / COLS
                onGridMeasured?.invoke(pos.x, pos.y, cellPx, gapPx)
            },
    ) {
        val cellSize: Dp = (maxWidth - (COLS - 1) * GAP_DP) / COLS

        val hoverAnchor = dragDropState?.hoverAnchor
        val draggedPiece = dragDropState?.draggedPiece
        val isDragging = dragDropState?.isDragging == true

        val hoverCells = remember(hoverAnchor, draggedPiece, isDragging) {
            if (isDragging && hoverAnchor != null && draggedPiece != null) {
                draggedPiece.shape.cells.map { (hoverAnchor.first + it.x) to (hoverAnchor.second + it.y) }.toSet()
            } else {
                emptySet()
            }
        }
        val hoverColorId = draggedPiece?.colorId
        val hoverValid = dragDropState?.isValidPlacement == true

        // Predictive clear preview
        val (predictedRows, predictedCols) = remember(grid, hoverCells, hoverValid) {
            if (!hoverValid || hoverCells.isEmpty()) {
                emptySet<Int>() to emptySet<Int>()
            } else {
                val rows = mutableSetOf<Int>()
                val cols = mutableSetOf<Int>()
                for (i in 0 until COLS) {
                    var rowFull = true
                    for (j in 0 until COLS) {
                        if (grid.isEmpty(j, i) && (j to i) !in hoverCells) { rowFull = false; break }
                    }
                    if (rowFull) rows.add(i)

                    var colFull = true
                    for (j in 0 until COLS) {
                        if (grid.isEmpty(i, j) && (i to j) !in hoverCells) { colFull = false; break }
                    }
                    if (colFull) cols.add(i)
                }
                rows to cols
            }
        }
        val hasPrediction = predictedRows.isNotEmpty() || predictedCols.isNotEmpty()
        val motionPolicy = gameMotionPolicy(
            comboLevel = comboLevel,
            hasDragHoverTarget = isDragging && hoverAnchor != null,
            hasPrediction = hasPrediction,
            reducedMotion = reducedMotion,
        )

        // Pulses are exposed as State<Float> and passed downward as () -> Float
        // lambdas so the read happens inside each cell's body — and only inside
        // the branches that actually use it. Cells that are neither hovered nor
        // in a predicted line never read the State, so they stay skippable and
        // don't recompose at the animation frame rate.
        val predictAlpha: () -> Float = if (motionPolicy.animatePredictionPulse) {
            val predictPulse = rememberInfiniteTransition(label = "predictPulse")
            val predictAlphaState = predictPulse.animateFloat(
                initialValue = 0.45f,
                targetValue = 0.72f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "predictAlpha",
            )
            remember(predictAlphaState) { { predictAlphaState.value } }
        } else {
            remember { { 0.62f } }
        }

        val hoverPulseAlpha: () -> Float = if (motionPolicy.animateHoverPulse) {
            val hoverPulse = rememberInfiniteTransition(label = "hoverPulse")
            val hoverPulseAlphaState = hoverPulse.animateFloat(
                initialValue = 0.62f,
                targetValue = 0.92f,
                animationSpec = infiniteRepeatable(
                    animation = tween(750, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "hoverPulseAlpha",
            )
            remember(hoverPulseAlphaState) { { hoverPulseAlphaState.value } }
        } else {
            remember { { 0.82f } }
        }

        val saturation by animateFloatAsState(
            targetValue = if (isGameOver) 0.2f else 1f,
            animationSpec = tween(400)
        )
        val colorFilter = remember(saturation) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
        }

        // Membership lookup for cleared cells — built once per event so each
        // cell can receive a primitive Boolean (stable, skippable param)
        // instead of the whole event list.
        val clearedSet = remember(clearedEvent) {
            buildSet { for (c in clearedEvent.cells) add(c.x to c.y) }
        }
        val tapEnabled = gridCellTapEnabled(
            interactive = interactive,
            hasSelectedPiece = selectedPiece != null,
        )

        // Wrap cells in a layer to apply saturation effect once to the whole
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.colorFilter = colorFilter },
        ) {
            for (row in 0 until COLS) {
                for (col in 0 until COLS) {
                    GridCell(
                        x = col,
                        y = row,
                        cellId = grid.colorAt(col, row),
                        cellSize = cellSize,
                        clearedNonce = clearedEvent.nonce,
                        isInClearedEvent = (col to row) in clearedSet,
                        isGameOver = isGameOver,
                        spatialMotionEnabled = motionPolicy.spatialMotionEnabled,
                        isHoverGhost = (col to row) in hoverCells,
                        hoverColorId = hoverColorId,
                        hoverValid = hoverValid,
                        hoverPulseAlpha = hoverPulseAlpha,
                        inPredictedLine = hasPrediction && (row in predictedRows || col in predictedCols),
                        predictAlpha = predictAlpha,
                        tapEnabled = tapEnabled,
                        onCellTapped = onCellTapped,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    x: Int,
    y: Int,
    cellId: Int,
    cellSize: Dp,
    clearedNonce: Int,
    isInClearedEvent: Boolean,
    isGameOver: Boolean,
    spatialMotionEnabled: Boolean,
    isHoverGhost: Boolean,
    hoverColorId: Int?,
    hoverValid: Boolean,
    hoverPulseAlpha: () -> Float,
    inPredictedLine: Boolean,
    predictAlpha: () -> Float,
    tapEnabled: Boolean,
    onCellTapped: (x: Int, y: Int) -> Unit,
) {
    val density = LocalDensity.current
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val cellAnim = remember { CellAnimState() }

    var displayColor by remember(cellId) { mutableIntStateOf(cellId) }
    var cellPrevNonce by remember { mutableIntStateOf(clearedNonce) }
    var isClearing by remember { mutableStateOf(false) }

    // Game-over board collapse
    LaunchedEffect(isGameOver) {
        if (isGameOver && cellId != -1) {
            val rowStagger = (COLS - 1 - y) * 35L
            val colJitter = x * 12L
            val falls = with(density) { 240.dp.toPx() }
            cellAnim.fall(
                delayMs = rowStagger + colJitter,
                distancePx = falls,
                spatialMotionEnabled = spatialMotionEnabled,
            )
        } else if (!isGameOver) {
            cellAnim.reset()
        }
    }

    // Placement animation detection
    var lastSeenCellId by remember { mutableIntStateOf(cellId) }
    LaunchedEffect(cellId) {
        if (cellId != -1 && lastSeenCellId == -1) {
            cellAnim.popIn(delayMs = 0L, spatialMotionEnabled = spatialMotionEnabled)
        }
        lastSeenCellId = cellId
    }

    // Clear animation detection
    LaunchedEffect(clearedNonce) {
        if (clearedNonce != cellPrevNonce && isInClearedEvent) {
            cellPrevNonce = clearedNonce
            isClearing = true
            cellAnim.clear(
                delayMs = (x + y) * 30L,
                spatialMotionEnabled = spatialMotionEnabled,
            )
            isClearing = false
            displayColor = -1
            cellAnim.reset()
        } else {
            cellPrevNonce = clearedNonce
        }
    }

    if (!isClearing) {
        displayColor = cellId
    }

    val isFilled = displayColor != -1
    val cellColor = when {
        isFilled -> pieceColor(displayColor)
        isHoverGhost && hoverColorId != null -> {
            val base = pieceColorPreview(hoverColorId)
            if (hoverValid) base.copy(alpha = base.alpha * hoverPulseAlpha())
            else base.copy(alpha = 0.15f)
        }
        else -> emptyColor
    }

    val predictGlow = if (inPredictedLine) predictAlpha() else 0f

    BlockPiece(
        color = cellColor,
        cellSize = cellSize,
        filled = isFilled || (isHoverGhost && hoverValid),
        scale = cellAnim.scale.value,
        scaleX = cellAnim.scaleX.value,
        scaleY = cellAnim.scaleY.value,
        alpha = cellAnim.alpha.value,
        flashAlpha = cellAnim.flashAlpha.value,
        rotationDeg = cellAnim.rotation.value,
        translateYPx = cellAnim.translateY.value,
        predictGlowAlpha = predictGlow,
        modifier = Modifier
            .offset(
                x = x * (cellSize + GAP_DP),
                y = y * (cellSize + GAP_DP),
            )
            .then(
                if (tapEnabled) {
                    Modifier.clickable { onCellTapped(x, y) }
                } else {
                    Modifier
                },
            ),
    )
}

internal fun gridCellTapEnabled(
    interactive: Boolean,
    hasSelectedPiece: Boolean,
): Boolean = interactive && hasSelectedPiece
