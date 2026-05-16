package ge.yet3.blokblast.screen.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch

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
            // Slot 0 flies in from the left, slot 2 from the right, slot 1
            // from below — a "slot-merge" entrance that reads as 3 distinct
            // pieces converging instead of one synchronized lift.
            val entrance = remember(trayKey, index) { Animatable(0f) }
            val (initialX, initialY) = when (index) {
                0 -> -160f to 30f
                2 -> 160f to 30f
                else -> 0f to 80f
            }
            val translateX = remember(trayKey, index) { Animatable(initialX) }
            val translateY = remember(trayKey, index) { Animatable(initialY) }
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
                        translateX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 320f))
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
                        translationX = translateX.value
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

    // Idle breathing & no-fit wiggle. Both are continuous animations and we
    // *must not* read their values in composition scope — doing so makes the
    // entire TraySlot recompose every frame (~60×/s × 3 slots). Instead we
    // hold onto the State<Float> objects and read .value inside the
    // graphicsLayer lambda below, which only invalidates the draw layer.
    val breathing = rememberInfiniteTransition(label = "breath")
    val breathScaleState = breathing.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val wiggleAngleState = wiggle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wiggleAngle",
    )

    // Discrete-only target — flips between fixed states (pressed / selected /
    // can-fit / no-fit), so the spring only runs on transitions, not every
    // frame. Breathing is layered on top inside graphicsLayer.
    val targetScale = when {
        isPressed -> 1.08f
        isSelected -> 1.12f
        canFit -> 1f
        else -> 0.92f
    }
    val scaleState = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(),
        label = "pieceScale",
    )
    val applyBreath = canFit && !isPressed && !isSelected
    val applyWiggle = !canFit && piece != null

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

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .padding(6.dp)
            .aspectRatio(1f)
            // Animated transforms read their State objects HERE (draw phase),
            // not in composition scope, so frame ticks don't recompose this
            // composable.
            .graphicsLayer {
                val s = scaleState.value
                val breath = if (applyBreath) breathScaleState.value else 1f
                val combined = s * breath
                scaleX = combined
                scaleY = combined
                rotationZ = if (applyWiggle) {
                    val a = wiggleAngleState.value
                    if (a < 0.05f) {
                        kotlin.math.sin(a / 0.05f * kotlin.math.PI.toFloat() * 4f) * 5f
                    } else 0f
                } else 0f
            }
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
                                            currentOnDragStart?.invoke(piece, startInWindow, downPos)
                                        }

                                        if (dragging) {
                                            change.consume()
                                            val posInWindow = slotOriginInWindow + change.position
                                            currentOnDragMove?.invoke(posInWindow)
                                        }
                                    }

                                    if (event.type == PointerEventType.Release) {
                                        isPressed = false
                                        if (dragging) {
                                            currentOnDragEnd?.invoke()
                                        } else {
                                            // It was a tap — toggle selection
                                            currentOnTap()
                                        }
                                        break
                                    }
                                }

                                // If the pointer was cancelled
                                if (isPressed) {
                                    isPressed = false
                                    if (dragging) currentOnDragEnd?.invoke()
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
                shimmerKey = piece.pieceId,
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
 *
 * Includes a one-shot diagonal shimmer that sweeps across the piece on each
 * fresh spawn (keyed by [shimmerKey]), masked to the actual cells via
 * offscreen compositing + SrcAtop.
 */
@Composable
private fun MiniPiece(
    shape: Polyomino,
    color: Color,
    cellSize: Dp = 10.dp,
    gap: Dp = 2.dp,
    shimmerKey: Any? = null,
) {
    val cols = shape.width
    val rows = shape.height
    val totalW = cols * cellSize + (cols - 1) * gap
    val totalH = rows * cellSize + (rows - 1) * gap

    val shimmer = remember(shimmerKey) { Animatable(-0.4f) }
    androidx.compose.runtime.LaunchedEffect(shimmerKey) {
        kotlinx.coroutines.delay(180)
        shimmer.snapTo(-0.4f)
        shimmer.animateTo(
            targetValue = 1.4f,
            animationSpec = tween(650, easing = LinearEasing),
        )
    }

    Box(
        modifier = Modifier
            .size(totalW, totalH)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val p = shimmer.value
                if (p in -0.4f..1.4f) {
                    val xCenter = p * size.width
                    val band = size.width * 0.18f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                            start = Offset(xCenter - band, 0f),
                            end = Offset(xCenter + band, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    ) {
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
