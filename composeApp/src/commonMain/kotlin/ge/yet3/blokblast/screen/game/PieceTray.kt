package ge.yet3.blokblast.screen.game

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.game.tray.PieceTrayComponent
import ge.yet.blockblast.feature.game.tray.TraySlotComponent
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet3.blokblast.theme.pieceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private typealias DragStart = (piece: Piece, startPosition: Offset, pieceOriginOffset: Offset) -> Unit
private typealias DragMove = (position: Offset) -> Unit
private typealias DragEnd = () -> Unit

private const val SLOT_COUNT = 3

/**
 * Bottom tray showing up to three selectable/draggable pieces.
 *
 * Slot identity is owned by [PieceTrayComponent] (keyed on `pieceId`), so
 * placing a piece keeps every survivor's component alive while `animateBounds`
 * slides the right-hand neighbours leftward to fill the freed slot. The
 * entrance Animatable is keyed on `pieceId` too, so it fires only for
 * newly-arrived pieces.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PieceTray(
    tray: PieceTrayComponent,
    modifier: Modifier = Modifier,
    onDragStart: DragStart? = null,
    onDragMove: DragMove? = null,
    onDragEnd: DragEnd? = null,
) {
    val slots by tray.slots.subscribeAsState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Each slot is exactly 1/3 of the tray, regardless of how many are
        // present — combined with Arrangement.Start this turns "neighbour
        // placed" into a fixed-distance leftward slide instead of a reflow.
        val slotWidth: Dp = maxWidth / SLOT_COUNT

        LookaheadScope {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slots.forEach { slot ->
                    key(slot.piece.pieceId) {
                        TraySlot(
                            slot = slot,
                            onDragStart = { piece, startPos, originOffset ->
                                tray.clearSelection()
                                onDragStart?.invoke(piece, startPos, originOffset)
                            },
                            onDragMove = onDragMove,
                            onDragEnd = onDragEnd,
                            modifier = Modifier
                                .width(slotWidth)
                                .animateBounds(this@LookaheadScope),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TraySlot(
    slot: TraySlotComponent,
    onDragStart: DragStart?,
    onDragMove: DragMove?,
    onDragEnd: DragEnd?,
    modifier: Modifier = Modifier,
) {
    val piece = slot.piece
    val isSelected by slot.isSelected.subscribeAsState()
    val canFit by slot.canFit.subscribeAsState()

    val entrance = rememberSlotEntrance(piece.pieceId, slot.spawnIndex)
    val ambient = rememberAmbientLoops()

    var isPressed by remember { mutableStateOf(false) }
    val isHighlighted = isSelected || isPressed

    val targetScale = when {
        isPressed -> 1.08f
        isSelected -> 1.12f
        canFit -> 1f
        else -> 0.92f
    }
    val pieceScale = animateFloatAsState(targetScale, animationSpec = spring(), label = "pieceScale")
    val applyBreath = canFit && !isPressed && !isSelected
    val applyWiggle = !canFit

    val pieceAlpha = animateFloatAsState(
        targetValue = if (canFit) 1f else 0.45f,
        animationSpec = tween(220),
        label = "pieceAlpha",
    )

    val pColor = pieceColor(piece.colorId)
    val slotBg = animateColorAsState(
        targetValue = if (isHighlighted) pColor.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(120),
        label = "slotBg",
    )
    val borderColor = if (isHighlighted) pColor else Color.Transparent

    Box(
        modifier = modifier
            .padding(6.dp)
            .aspectRatio(1f)
            // First layer: entrance fly-in (keyed on pieceId, fires once per
            // fresh piece). Second layer: idle/press transforms that read
            // animated values inside the layer lambda so frame ticks don't
            // recompose us — only the draw layer invalidates.
            .graphicsLayer {
                scaleX = entrance.scale.value
                scaleY = entrance.scale.value
                alpha = entrance.scale.value
                translationX = entrance.translateX.value
                translationY = entrance.translateY.value
            }
            .graphicsLayer {
                val s = pieceScale.value
                val breath = if (applyBreath) ambient.breathScale.value else 1f
                val combined = s * breath
                scaleX = combined
                scaleY = combined
                rotationZ = if (applyWiggle) wiggleAngle(ambient.wigglePhase.value) else 0f
            }
            .clip(RoundedCornerShape(14.dp))
            .drawBehind { drawRect(slotBg.value) }
            .then(
                if (isHighlighted) Modifier.border(2.dp, borderColor, RoundedCornerShape(14.dp))
                else Modifier,
            )
            .traySlotPointerInput(
                piece = piece,
                onPressedChange = { isPressed = it },
                onTap = slot::onTap,
                onDragStart = onDragStart,
                onDragMove = onDragMove,
                onDragEnd = onDragEnd,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val visibleColor = if (isHighlighted) pColor else pColor.copy(alpha = 0.6f)
        Box(modifier = Modifier.graphicsLayer { alpha = pieceAlpha.value }) {
            MiniPiece(
                shape = piece.shape,
                color = visibleColor,
                shimmerKey = piece.pieceId,
            )
        }
    }
}

/* ────────────────────────────── Animation helpers ─────────────────────────── */

private class SlotEntrance(
    val scale: Animatable<Float, *>,
    val translateX: Animatable<Float, *>,
    val translateY: Animatable<Float, *>,
)

/**
 * Spring-overshoot entrance, staggered by [spawnIndex]: slot 0 flies in from
 * the left, slot 2 from the right, slot 1 from below. Keyed on [pieceId] so
 * survivors of a partial placement keep their already-settled state.
 */
@Composable
private fun rememberSlotEntrance(pieceId: Long, spawnIndex: Int): SlotEntrance {
    val (initialX, initialY) = when (spawnIndex) {
        0 -> -160f to 30f
        2 -> 160f to 30f
        else -> 0f to 80f
    }
    val scale = remember(pieceId) { Animatable(0f) }
    val translateX = remember(pieceId) { Animatable(initialX) }
    val translateY = remember(pieceId) { Animatable(initialY) }
    LaunchedEffect(pieceId) {
        delay(spawnIndex * 80L)
        launch {
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 380f),
            )
        }
        launch { translateX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 320f)) }
        launch { translateY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    }
    return remember(pieceId) { SlotEntrance(scale, translateX, translateY) }
}

private class AmbientLoops(
    val breathScale: androidx.compose.runtime.State<Float>,
    val wigglePhase: androidx.compose.runtime.State<Float>,
)

/**
 * Continuous breathing + wiggle phases. Returned as `State<Float>` (not `Float`)
 * so callers must read them inside a draw-phase lambda — reading in composition
 * scope would recompose the whole slot 60×/s.
 */
@Composable
private fun rememberAmbientLoops(): AmbientLoops {
    val breath = rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    val wiggle = rememberInfiniteTransition(label = "wiggle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wigglePhase",
    )
    return remember { AmbientLoops(breath, wiggle) }
}

/** Short bursts of rotation at the start of each wiggle cycle. */
private fun wiggleAngle(phase: Float): Float =
    if (phase < 0.05f) {
        kotlin.math.sin(phase / 0.05f * kotlin.math.PI.toFloat() * 4f) * 5f
    } else {
        0f
    }

/* ──────────────────────────────── Pointer input ───────────────────────────── */

/**
 * Single-finger tap + long-press-drag handler. Drag starts after the pointer
 * travels past `touchSlop`; a release without crossing slop is a tap.
 */
@Composable
private fun Modifier.traySlotPointerInput(
    piece: Piece,
    onPressedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDragStart: DragStart?,
    onDragMove: DragMove?,
    onDragEnd: DragEnd?,
): Modifier {
    var slotOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    val onDragStartLatest by rememberUpdatedState(onDragStart)
    val onDragMoveLatest by rememberUpdatedState(onDragMove)
    val onDragEndLatest by rememberUpdatedState(onDragEnd)
    val onTapLatest by rememberUpdatedState(onTap)
    val onPressedChangeLatest by rememberUpdatedState(onPressedChange)

    return this
        .onGloballyPositioned { coords -> slotOriginInWindow = coords.positionInWindow() }
        .pointerInput(piece.pieceId) {
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent()
                    if (downEvent.type != PointerEventType.Press) continue
                    val downChange = downEvent.changes.firstOrNull() ?: continue

                    onPressedChangeLatest(true)
                    val downPos = downChange.position
                    var dragging = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        when (event.type) {
                            PointerEventType.Move -> {
                                val delta = change.position - downPos
                                if (!dragging && delta.getDistance() > touchSlop) {
                                    dragging = true
                                    onDragStartLatest?.invoke(
                                        piece,
                                        slotOriginInWindow + downPos,
                                        downPos,
                                    )
                                }
                                if (dragging) {
                                    change.consume()
                                    onDragMoveLatest?.invoke(slotOriginInWindow + change.position)
                                }
                            }
                            PointerEventType.Release -> {
                                onPressedChangeLatest(false)
                                if (dragging) onDragEndLatest?.invoke() else onTapLatest()
                                break
                            }
                        }
                    }

                    // Defensive: cancel paths skip the Release branch.
                    onPressedChangeLatest(false)
                    if (dragging) onDragEndLatest?.invoke()
                }
            }
        }
}

/* ────────────────────────────── Piece rendering ───────────────────────────── */

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
    LaunchedEffect(shimmerKey) {
        delay(180)
        shimmer.snapTo(-0.4f)
        shimmer.animateTo(1.4f, tween(650, easing = LinearEasing))
    }

    Box(
        modifier = Modifier
            .size(totalW, totalH)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
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
