package ge.yet3.blokblast.screen.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet3.blokblast.theme.pieceColor

/**
 * Bottom tray showing up to three selectable/draggable pieces.
 */
@Composable
fun PieceTray(
    pieces: List<Piece>,
    selectedPieceId: Long?,
    onPieceSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    grid: Grid? = null,
    onDragStart: ((piece: Piece, startPosition: Offset, pieceOriginOffset: Offset) -> Unit)? = null,
    onDragMove: ((position: Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val trayKey = remember(pieces) { pieces.map { it.pieceId }.joinToString() }

        repeat(3) { index ->
            val piece = pieces.getOrNull(index)
            val isSelected = piece != null && piece.pieceId == selectedPieceId

            // Spring-overshoot entrance, staggered per slot.
            val entrance = remember(trayKey, index) { Animatable(0f) }
            val translateY = remember(trayKey, index) { Animatable(40f) }
            androidx.compose.runtime.LaunchedEffect(trayKey, index) {
                kotlinx.coroutines.delay(index * 80L)
                kotlinx.coroutines.coroutineScope {
                    launch {
                        entrance.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 380f),
                        )
                    }
                    launch {
                        translateY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 380f))
                    }
                }
            }

            // Can this piece fit anywhere on the board? Drives dim-when-no-fit.
            val canFit = remember(piece, grid) {
                if (piece == null || grid == null) true
                else canPlaceAnywhere(piece.shape, grid)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = entrance.value
                        scaleY = entrance.value
                        alpha = entrance.value
                        translationY = translateY.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                TraySlot(
                    piece = piece,
                    isSelected = isSelected,
                    canFit = canFit,
                    onTap = { if (piece != null) onPieceSelected(piece.pieceId) },
                    onDragStart = onDragStart,
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TraySlot(
    piece: Piece?,
    isSelected: Boolean,
    canFit: Boolean,
    onTap: () -> Unit,
    onDragStart: ((piece: Piece, startPosition: Offset, pieceOriginOffset: Offset) -> Unit)?,
    onDragMove: ((position: Offset) -> Unit)?,
    onDragEnd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val isHighlighted = isSelected || isPressed

    // Idle breathing — subtle pulse on pieces that can still be placed.
    val breathing = rememberInfiniteTransition(label = "breath")
    val breathScale by breathing.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    val targetScale = when {
        isPressed -> 1.08f
        isSelected -> 1.12f
        canFit -> breathScale
        else -> 0.92f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(),
        label = "pieceScale",
    )

    // Dim/desaturate when this piece can't be placed anywhere.
    val pieceAlpha by animateFloatAsState(
        targetValue = if (canFit) 1f else 0.45f,
        animationSpec = tween(220),
        label = "pieceAlpha",
    )

    val pColor = piece?.let { pieceColor(it.colorId) }

    val slotBg by animateColorAsState(
        targetValue = when {
            isHighlighted && pColor != null -> pColor.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(120),
        label = "slotBg",
    )

    val borderColor = when {
        isHighlighted && pColor != null -> pColor
        else -> Color.Transparent
    }

    var slotOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    Box(
        modifier = modifier
            .padding(6.dp)
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(slotBg)
            .then(
                if (isHighlighted) Modifier.border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(14.dp),
                ) else Modifier,
            )
            .onGloballyPositioned { coords ->
                slotOriginInWindow = coords.positionInWindow()
            }
            .then(
                if (piece != null) {
                    Modifier.pointerInput(piece.pieceId) {
                        awaitPointerEventScope {
                            while (true) {
                                // Wait for finger down
                                val down = awaitPointerEvent()
                                if (down.type != PointerEventType.Press) continue
                                val downChange = down.changes.firstOrNull() ?: continue

                                isPressed = true
                                val downPos = downChange.position
                                var dragging = false
                                var totalDrag = Offset.Zero

                                // Track move / up
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (event.type == PointerEventType.Move) {
                                        val delta = change.position - downPos
                                        totalDrag = delta

                                        if (!dragging && delta.getDistance() > touchSlop) {
                                            dragging = true
                                            val startInWindow = slotOriginInWindow + downPos
                                            onDragStart?.invoke(piece, startInWindow, downPos)
                                        }

                                        if (dragging) {
                                            change.consume()
                                            val posInWindow = slotOriginInWindow + change.position
                                            onDragMove?.invoke(posInWindow)
                                        }
                                    }

                                    if (event.type == PointerEventType.Release) {
                                        isPressed = false
                                        if (dragging) {
                                            onDragEnd?.invoke()
                                        } else {
                                            // It was a tap — toggle selection
                                            onTap()
                                        }
                                        break
                                    }
                                }

                                // If the pointer was cancelled
                                if (isPressed) {
                                    isPressed = false
                                    if (dragging) onDragEnd?.invoke()
                                }
                            }
                        }
                    }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (piece != null) {
            val baseColor = pieceColor(piece.colorId)
            val visibleColor = (if (isHighlighted) baseColor else baseColor.copy(alpha = 0.6f))
                .copy(alpha = baseColor.alpha * pieceAlpha)
            MiniPiece(
                shape = piece.shape,
                color = visibleColor,
            )
        }
    }
}

private fun canPlaceAnywhere(shape: Polyomino, grid: Grid): Boolean {
    for (y in 0 until Grid.SIZE) {
        for (x in 0 until Grid.SIZE) {
            if (canPlacePiece(shape, x, y, grid)) return true
        }
    }
    return false
}

/**
 * Renders a polyomino shape as tiny 3D-like [BlockPiece] cells.
 */
@Composable
private fun MiniPiece(
    shape: Polyomino,
    color: Color,
    cellSize: Dp = 10.dp,
    gap: Dp = 2.dp,
) {
    val cols = shape.width
    val rows = shape.height
    val totalW = cols * cellSize + (cols - 1) * gap
    val totalH = rows * cellSize + (rows - 1) * gap

    Box(modifier = Modifier.size(totalW, totalH)) {
        shape.cells.forEach { pos ->
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
