package ge.yet3.blokblast.screen.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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

            var visible by remember(trayKey) { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(trayKey) {
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.weight(1f),
                enter = slideInVertically(
                    animationSpec = tween(350, delayMillis = index * 80)
                ) { it / 2 } + fadeIn(tween(350, delayMillis = index * 80)) + scaleIn(
                    initialScale = 0.7f,
                    animationSpec = tween(350, delayMillis = index * 80)
                )
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TraySlot(
                        piece = piece,
                        isSelected = isSelected,
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
}

@Composable
private fun TraySlot(
    piece: Piece?,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDragStart: ((piece: Piece, startPosition: Offset, pieceOriginOffset: Offset) -> Unit)?,
    onDragMove: ((position: Offset) -> Unit)?,
    onDragEnd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val isHighlighted = isSelected || isPressed

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 1.08f
            isSelected -> 1.12f
            else -> 1f
        },
        animationSpec = spring(),
        label = "pieceScale",
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
            MiniPiece(
                shape = piece.shape,
                color = if (isHighlighted) pieceColor(piece.colorId) else pieceColor(piece.colorId).copy(alpha = 0.6f),
            )
        }
    }
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
