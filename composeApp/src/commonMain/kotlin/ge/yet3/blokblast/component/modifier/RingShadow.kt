package ge.yet3.blokblast.component.modifier

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Anthropic-style "Ring Shadow" — a single hairline border that traces the
 * shape of a button or card.  Mirrors the CSS `box-shadow: 0 0 0 1px <color>`
 * pattern used across Anthropic surfaces in place of heavy drop shadows.
 *
 * The ring sits *inside* the layout bounds, so the composable does not grow
 * by [width].  Apply this **after** any `clip` and **before** `background`.
 *
 * @param color  Ring color.  Defaults to a warm neutral and should usually be
 *               sourced from `MaterialTheme.colorScheme.outline` at the call
 *               site.
 * @param width  Ring thickness.  Default 1.dp matches Anthropic's hairline.
 * @param shape  Shape that defines the ring path.
 */
fun Modifier.ringShadow(
    color: Color,
    shape: Shape,
    width: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .border(BorderStroke(width, color), shape)
