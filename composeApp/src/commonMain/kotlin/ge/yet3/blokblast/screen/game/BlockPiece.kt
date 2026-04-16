package ge.yet3.blokblast.screen.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single block cell rendered as a 3D-like composable — no Canvas.
 *
 * The visual is achieved by layering three Boxes:
 * 1. **Base fill** — the piece color fills the entire cell.
 * 2. **Highlight inset** — a lighter semi-transparent strip on the top and left
 *    edges, simulating a light source from top-left.
 * 3. **Shadow inset** — a darker semi-transparent strip on the bottom and right
 *    edges, simulating shadow from the same light source.
 *
 * All layers are standard Compose UI (`Box`, `Modifier.background`, padding),
 * meaning every block can be individually targeted with `Modifier.scale`,
 * `Modifier.rotate`, `Modifier.offset`, etc., for animations later.
 *
 * @param color    Base piece color.
 * @param cellSize Outer size of the block.
 * @param filled   Whether this cell is actually occupied (false = empty grid cell).
 */
@Composable
fun BlockPiece(
    color: Color,
    cellSize: Dp,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val shape = RoundedCornerShape(4.dp)
    val bevelWidth = (cellSize.value * 0.12f).coerceIn(1.5f, 4f).dp

    Box(
        modifier = modifier
            .size(cellSize)
            .clip(shape)
            .background(color),
    ) {
        if (filled) {
            // Top-left highlight (lighter edge)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bevelWidth, end = bevelWidth)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            // Bottom-right shadow (darker edge)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = bevelWidth, start = bevelWidth)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.18f)),
            )
            // Inner face (the "top" of the block)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bevelWidth)
                    .clip(shape)
                    .background(color),
            )
        }
    }
}
