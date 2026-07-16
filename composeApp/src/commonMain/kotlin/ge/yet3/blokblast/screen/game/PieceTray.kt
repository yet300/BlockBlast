package ge.yet3.blokblast.screen.game

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalDensity
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

private typealias DragStart = (
    piece: Piece,
    startPosition: Offset,
    pieceOriginOffset: Offset,
    sourcePosition: Offset,
) -> Unit
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
    dragEnabled: Boolean = true,
    spatialMotionEnabled: Boolean = true,
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
                            onDragStart = { piece, startPos, originOffset, sourcePosition ->
                                tray.clearSelection()
                                onDragStart?.invoke(piece, startPos, originOffset, sourcePosition)
                            },
                            onDragMove = onDragMove,
                            onDragEnd = onDragEnd,
                            dragEnabled = dragEnabled,
                            spatialMotionEnabled = spatialMotionEnabled,
                            modifier = Modifier
                                .width(slotWidth)
                                .then(
                                    if (spatialMotionEnabled) {
                                        Modifier.animateBounds(this@LookaheadScope)
                                    } else {
                                        Modifier
                                    },
                                ),
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
    dragEnabled: Boolean,
    spatialMotionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val piece = slot.piece
    val isSelected by slot.isSelected.subscribeAsState()
    val canFit by slot.canFit.subscribeAsState()

    val entrance = rememberSlotEntrance(
        pieceId = piece.pieceId,
        spawnIndex = slot.spawnIndex,
        spatialMotionEnabled = spatialMotionEnabled,
    )
    var isPressed by remember { mutableStateOf(false) }
    val isHighlighted = isSelected || isPressed

    val targetScale = when {
        isPressed -> 1.08f
        isSelected -> 1.12f
        canFit -> 1f
        else -> 0.92f
    }
    val pieceScale = if (spatialMotionEnabled) {
        animateFloatAsState(targetScale, animationSpec = spring(), label = "pieceScale")
    } else {
        null
    }
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
            // A restrained, non-overshooting entrance keeps tray refreshes
            // legible without competing with the grid for attention.
            .graphicsLayer {
                scaleX = entrance.scale.value
                scaleY = entrance.scale.value
                alpha = entrance.alpha.value
                translationY = entrance.translateY.value
            }
            .graphicsLayer {
                scaleX = pieceScale?.value ?: 1f
                scaleY = pieceScale?.value ?: 1f
            }
            .clip(RoundedCornerShape(14.dp))
            .drawBehind { drawRect(slotBg.value) }
            .then(
                if (isHighlighted) Modifier.border(2.dp, borderColor, RoundedCornerShape(14.dp))
                else Modifier,
            )
            .traySlotPointerInput(
                piece = piece,
                enabled = dragEnabled,
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
                spatialMotionEnabled = spatialMotionEnabled,
            )
        }
    }
}

/* ────────────────────────────── Animation helpers ─────────────────────────── */

private class SlotEntrance(
    val scale: Animatable<Float, *>,
    val alpha: Animatable<Float, *>,
    val translateY: Animatable<Float, *>,
)

/**
 * Compact entrance keyed on [pieceId], so survivors keep their settled state.
 * A short stagger communicates that a new tray was dealt without turning the
 * refresh into a sequence the player has to wait through.
 */
@Composable
private fun rememberSlotEntrance(
    pieceId: Long,
    spawnIndex: Int,
    spatialMotionEnabled: Boolean,
): SlotEntrance {
    val initialY = with(LocalDensity.current) { 8.dp.toPx() }
    val scale = remember(pieceId, spatialMotionEnabled) {
        Animatable(if (spatialMotionEnabled) 0.95f else 1f)
    }
    val alpha = remember(pieceId, spatialMotionEnabled) {
        Animatable(if (spatialMotionEnabled) 0f else 1f)
    }
    val translateY = remember(pieceId, spatialMotionEnabled) {
        Animatable(if (spatialMotionEnabled) initialY else 0f)
    }
    LaunchedEffect(pieceId, spatialMotionEnabled) {
        if (!spatialMotionEnabled) return@LaunchedEffect
        delay(spawnIndex * 40L)
        val settle = tween<Float>(durationMillis = 180, easing = LinearOutSlowInEasing)
        launch { scale.animateTo(1f, settle) }
        launch { alpha.animateTo(1f, settle) }
        launch { translateY.animateTo(0f, settle) }
    }
    return remember(pieceId, spatialMotionEnabled) { SlotEntrance(scale, alpha, translateY) }
}

/* ──────────────────────────────── Pointer input ───────────────────────────── */

/**
 * Single-finger tap + long-press-drag handler. Drag starts after the pointer
 * travels past `touchSlop`; a release without crossing slop is a tap.
 */
@Composable
private fun Modifier.traySlotPointerInput(
    piece: Piece,
    enabled: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDragStart: DragStart?,
    onDragMove: DragMove?,
    onDragEnd: DragEnd?,
): Modifier {
    if (!enabled) return this

    var slotOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var slotCenterInWindow by remember { mutableStateOf(Offset.Zero) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    val onDragStartLatest by rememberUpdatedState(onDragStart)
    val onDragMoveLatest by rememberUpdatedState(onDragMove)
    val onDragEndLatest by rememberUpdatedState(onDragEnd)
    val onTapLatest by rememberUpdatedState(onTap)
    val onPressedChangeLatest by rememberUpdatedState(onPressedChange)

    return this
        .onGloballyPositioned { coords ->
            slotOriginInWindow = coords.positionInWindow()
            slotCenterInWindow = slotOriginInWindow + Offset(
                x = coords.size.width / 2f,
                y = coords.size.height / 2f,
            )
        }
        .pointerInput(piece.pieceId, enabled) {
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent()
                    if (downEvent.type != PointerEventType.Press) continue
                    val downChange = downEvent.changes.firstOrNull() ?: continue

                    onPressedChangeLatest(true)
                    val downPos = downChange.position
                    var dragging = false
                    var endedNormally = false

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
                                        slotCenterInWindow,
                                    )
                                }
                                if (dragging) {
                                    change.consume()
                                    onDragMoveLatest?.invoke(slotOriginInWindow + change.position)
                                }
                            }
                            PointerEventType.Release -> {
                                endedNormally = true
                                onPressedChangeLatest(false)
                                if (dragging) onDragEndLatest?.invoke() else onTapLatest()
                                break
                            }
                        }
                    }

                    // Defensive: cancel paths skip the Release branch.
                    onPressedChangeLatest(false)
                    if (dragging && !endedNormally) onDragEndLatest?.invoke()
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
    spatialMotionEnabled: Boolean,
) {
    val cols = shape.width
    val rows = shape.height
    val totalW = cols * cellSize + (cols - 1) * gap
    val totalH = rows * cellSize + (rows - 1) * gap

    val shimmer = remember(shimmerKey, spatialMotionEnabled) {
        Animatable(if (spatialMotionEnabled) -0.4f else 1.4f)
    }
    LaunchedEffect(shimmerKey, spatialMotionEnabled) {
        if (!spatialMotionEnabled) return@LaunchedEffect
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
