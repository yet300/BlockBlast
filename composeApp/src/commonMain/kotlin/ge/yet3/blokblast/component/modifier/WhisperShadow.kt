package ge.yet3.blokblast.component.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Anthropic-style "Whisper Shadow" — a barely-there elevation cue used for
 * floating cards (best-score chip, settings sheet, game-over card).
 *
 * Mirrors the CSS `box-shadow: rgba(0,0,0,0.05) 0px 4px 24px` token from the
 * Anthropic web design system.  Implemented on top of Compose's standard
 * `Modifier.shadow` (which is the cross-platform soft-shadow primitive
 * available in Compose Multiplatform 1.10) with a tinted ambient/spot color so
 * the resulting shadow keeps the ~5% black opacity instead of Material's
 * default heavy black.
 *
 * @param shape       Shape used both for the shadow silhouette and elevation
 *                    clip.
 * @param elevation   Visual blur radius.  Default 24.dp matches the design
 *                    token; pass higher values when a piece is being dragged.
 * @param shadowColor Tint of both ambient and spot shadow.  Default keeps the
 *                    shadow at ~5% opacity black.
 */
fun Modifier.whisperShadow(
    shape: Shape,
    elevation: Dp = 24.dp,
    shadowColor: Color = Color.Black.copy(alpha = 0.05f),
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = shadowColor,
    spotColor = shadowColor,
)
