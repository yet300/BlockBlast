package ge.yet3.blokblast.component.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Dynamic shadow used while a polyomino piece is being dragged.
 *
 * The shadow grows with [lift] (0f = resting on the tray, 1f = fully picked
 * up) and is tinted with the piece color so it reads warm and "alive" instead
 * of the default cold Material drop shadow.
 *
 * Drives both the blur radius and the tint alpha from a single 0..1 parameter
 * so callers can wire it into a `Animatable<Float>` and animate the lift.
 *
 * @param pieceColor The piece's base color — used to tint the cast shadow.
 * @param shape      Shape used as the shadow silhouette (usually a rounded
 *                   rectangle matching the block bevel).
 * @param lift       0f..1f — how much the piece is lifted off the surface.
 *                   Values are clamped.
 */
fun Modifier.liftedPieceShadow(
    pieceColor: Color,
    shape: Shape,
    lift: Float,
): Modifier {
    val clamped = lift.coerceIn(0f, 1f)
    val elevationDp = (4 + (clamped * 24f).roundToInt()).dp
    val tinted = pieceColor.copy(alpha = 0.15f + clamped * 0.25f)
    return this.shadow(
        elevation = elevationDp,
        shape = shape,
        clip = false,
        ambientColor = tinted,
        spotColor = tinted,
    )
}
