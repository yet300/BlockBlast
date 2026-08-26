package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TwentyFortyEightTile(
    value: Long,
    modifier: Modifier = Modifier,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        TileTheme.Dark
    } else {
        TileTheme.Light
    }
    val style = TileStylePolicy.style(value, theme)
    val shape = RoundedCornerShape(10.dp)
    val markColor = (style.outline ?: style.foreground).copy(alpha = 0.32f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(style.background)
            .then(
                style.outline?.let { outline ->
                    Modifier.border(width = 1.dp, color = outline, shape = shape)
                } ?: Modifier,
            )
            .drawBehind {
                repeat(style.insetMarkCount) { index ->
                    val inset = (4.dp * (index + 1)).toPx()
                    val strokeWidth = 1.dp.toPx()
                    drawRoundRect(
                        color = markColor,
                        topLeft = Offset(inset, inset),
                        size = Size(
                            width = size.width - inset * 2f,
                            height = size.height - inset * 2f,
                        ),
                        cornerRadius = CornerRadius((8.dp - index.dp).toPx()),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val typography = MaterialTheme.typography.headlineMedium
        Text(
            text = value.toString(),
            modifier = Modifier.clearAndSetSemantics {},
            color = style.foreground,
            autoSize = TextAutoSize.StepBased(
                minFontSize = MinimumTileFontSize,
                maxFontSize = typography.fontSize * style.textScale,
                stepSize = 0.25.sp,
            ),
            style = typography.copy(
                fontSize = typography.fontSize * style.textScale,
                lineHeight = TextUnit.Unspecified,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            onTextLayout = onTextLayout,
        )
    }
}

private val MinimumTileFontSize = 7.sp
