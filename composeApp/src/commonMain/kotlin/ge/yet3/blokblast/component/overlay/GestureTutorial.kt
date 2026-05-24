package ge.yet3.blokblast.component.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.util.lerp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.tutorial_grid_title
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet3.blokblast.screen.game.BlockPiece
import ge.yet3.blokblast.screen.game.effects.ConfettiEffect
import ge.yet3.blokblast.theme.pieceColor
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Fingertip hotspot as a fraction of the pointer canvas — the index-finger tip
// that the gesture aligns to, and the pivot the press-scale shrinks toward.
private const val TIP_X = 0.41f
private const val TIP_Y = 0.05f

private val POINTER_W = 64.dp
private val POINTER_H = 74.dp
private val GHOST_CELL = 26.dp
private val GHOST_GAP = 3.dp

// How far above the fingertip the lifted ghost piece floats, mirroring the
// vertical lift of a real drag so the demo reads like the real gesture.
private val GHOST_LIFT = 30.dp

/**
 * Wordless first-launch onboarding: a translucent scrim dims the screen while
 * an animated hand loops the core gesture — lift the first tray piece and drag
 * it onto the board — with a ghost copy of [piece] following the fingertip.
 *
 * Touches pass straight through: the player dismisses it simply by performing
 * the gesture themselves. The caller flips [dismissing] on first engagement,
 * which fires a confetti burst and a fade-out before [onExitComplete] runs
 * (where the caller persists the "seen" flag and unmounts this overlay).
 * [trayBounds] and [gridBounds] are in root pixels.
 */
@Composable
fun GestureTutorial(
    trayBounds: Rect,
    gridBounds: Rect,
    piece: Piece?,
    captionTopPadding: Dp,
    dismissing: Boolean,
    onExitComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (piece == null || trayBounds == Rect.Zero || gridBounds == Rect.Zero) return

    val density = LocalDensity.current
    val cornerPx = with(density) { 16.dp.toPx() }
    val padPx = with(density) { 6.dp.toPx() }

    // Start at the centre of the first tray slot (left third), end near the
    // centre of the board.
    val trayPoint = Offset(trayBounds.left + trayBounds.width / 6f, trayBounds.center.y)
    val boardPoint = gridBounds.center

    val cols = piece.shape.width
    val rows = piece.shape.height
    val ghostWPx = with(density) { (cols * GHOST_CELL + (cols - 1) * GHOST_GAP).toPx() }
    val ghostHPx = with(density) { (rows * GHOST_CELL + (rows - 1) * GHOST_GAP).toPx() }
    val pointerWPx = with(density) { POINTER_W.toPx() }
    val pointerHPx = with(density) { POINTER_H.toPx() }
    val liftPx = with(density) { GHOST_LIFT.toPx() }

    val progress = remember { Animatable(0f) }
    val pointerAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    var pressed by remember { mutableStateOf(false) }
    var ghostVisible by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    // Demo gesture loop — halts the moment the player engages so it doesn't
    // keep moving under the fade-out.
    LaunchedEffect(trayPoint, boardPoint, dismissing) {
        if (dismissing) return@LaunchedEffect
        while (true) {
            progress.snapTo(0f)
            pressed = false
            ghostVisible = false
            pointerAlpha.snapTo(0f)
            pointerAlpha.animateTo(1f, tween(250))
            delay(400)
            pressed = true
            delay(260)
            ghostVisible = true
            progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
            delay(160)
            pressed = false
            delay(140)
            ghostVisible = false
            delay(420)
            pointerAlpha.animateTo(0f, tween(300))
            delay(420)
        }
    }

    // Exit: pop the confetti, fade the scrim/hand away, then hand control back
    // to the caller (which persists "seen" and unmounts us). We stay mounted a
    // beat longer so the confetti has time to fall.
    LaunchedEffect(dismissing) {
        if (dismissing) {
            showConfetti = true
            exitAlpha.animateTo(0f, tween(380))
            delay(1100)
            onExitComplete()
        }
    }

    val scrimColor = Color.Black.copy(alpha = 0.5f)
    val ringColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
      Box(
        modifier = Modifier
            .fillMaxSize()
            // BlendMode.Clear needs an offscreen layer to actually punch holes;
            // the same layer's alpha drives the fade-out.
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = exitAlpha.value
            }
            .drawWithCache {
                onDrawWithContent {
                    drawRect(scrimColor)
                    drawSpotlight(trayBounds, padPx, cornerPx, ringColor)
                    drawSpotlight(gridBounds, padPx, cornerPx, ringColor)
                    drawContent()
                }
            },
    ) {
        // Short caption, parked just under the score bar.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = captionTopPadding)
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(Res.string.tutorial_grid_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Ghost piece — lifts off the tray and rides above the fingertip.
        if (ghostVisible) {
            GhostPiece(
                shape = piece.shape,
                color = pieceColor(piece.colorId),
                modifier = Modifier.offset {
                    val x = lerp(trayPoint.x, boardPoint.x, progress.value)
                    val y = lerp(trayPoint.y, boardPoint.y, progress.value) - liftPx
                    IntOffset(
                        (x - ghostWPx / 2f).roundToInt(),
                        (y - ghostHPx / 2f).roundToInt(),
                    )
                },
            )
        }

        // The pointing hand, fingertip pinned to the current gesture point.
        val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(180), label = "press")
        val rippleAlpha by animateFloatAsState(if (pressed) 0.35f else 0f, tween(180), label = "ripple")
        Canvas(
            modifier = Modifier
                .offset {
                    val x = lerp(trayPoint.x, boardPoint.x, progress.value)
                    val y = lerp(trayPoint.y, boardPoint.y, progress.value)
                    IntOffset(
                        (x - pointerWPx * TIP_X).roundToInt(),
                        (y - pointerHPx * TIP_Y).roundToInt(),
                    )
                }
                .size(POINTER_W, POINTER_H)
                .graphicsLayer {
                    alpha = pointerAlpha.value
                    scaleX = pressScale
                    scaleY = pressScale
                    transformOrigin = TransformOrigin(TIP_X, TIP_Y)
                },
        ) {
            if (rippleAlpha > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = rippleAlpha),
                    radius = size.width * 0.32f,
                    center = Offset(size.width * TIP_X, size.height * TIP_Y),
                )
            }
            drawHand()
        }
      }

        // Celebration burst — drawn outside the faded layer so it stays vivid.
        if (showConfetti) ConfettiEffect()
    }
}

/** Punches a rounded transparent hole over [target] and outlines it faintly. */
private fun DrawScope.drawSpotlight(target: Rect, padPx: Float, cornerPx: Float, ring: Color) {
    if (target == Rect.Zero) return
    val topLeft = Offset(target.left - padPx, target.top - padPx)
    val size = Size(target.width + 2f * padPx, target.height + 2f * padPx)
    val corner = CornerRadius(cornerPx, cornerPx)
    drawRoundRect(
        color = Color.Transparent,
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        blendMode = BlendMode.Clear,
    )
    drawRoundRect(
        color = ring.copy(alpha = 0.6f),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
    )
}

/** Draws a stylised pointing hand (index finger up) filling the canvas. */
private fun DrawScope.drawHand() {
    val w = size.width
    val h = size.height
    // Soft drop shadow, then the white hand on top — overlapping same-colour
    // shapes hide their seams, so no outline is needed.
    drawHandShapes(w, h, Color.Black.copy(alpha = 0.22f), Offset(w * 0.04f, h * 0.05f))
    drawHandShapes(w, h, Color.White, Offset.Zero)
}

private fun DrawScope.drawHandShapes(w: Float, h: Float, color: Color, o: Offset) {
    // Palm / fist.
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.16f + o.x, h * 0.40f + o.y),
        size = Size(w * 0.74f, h * 0.58f),
        cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
    )
    // Index finger (capsule).
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.30f + o.x, h * 0.02f + o.y),
        size = Size(w * 0.22f, h * 0.52f),
        cornerRadius = CornerRadius(w * 0.11f, w * 0.11f),
    )
    // Thumb (capsule, angled out to the left).
    rotate(degrees = -28f, pivot = Offset(w * 0.24f + o.x, h * 0.62f + o.y)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.02f + o.x, h * 0.52f + o.y),
            size = Size(w * 0.40f, w * 0.22f),
            cornerRadius = CornerRadius(w * 0.11f, w * 0.11f),
        )
    }
}

@Composable
private fun GhostPiece(
    shape: Polyomino,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val w = shape.width * GHOST_CELL + (shape.width - 1) * GHOST_GAP
    val h = shape.height * GHOST_CELL + (shape.height - 1) * GHOST_GAP
    Box(modifier = modifier.size(w, h)) {
        shape.cells.forEach { pos ->
            BlockPiece(
                color = color,
                cellSize = GHOST_CELL,
                filled = true,
                modifier = Modifier.offset(
                    x = pos.x * (GHOST_CELL + GHOST_GAP),
                    y = pos.y * (GHOST_CELL + GHOST_GAP),
                ),
            )
        }
    }
}
