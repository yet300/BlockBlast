package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.best_score
import ge.yet.game.fruitmerge.generated.resources.score
import ge.yet.game.uikit.components.score.compactScore
import org.jetbrains.compose.resources.stringResource

internal val MarketCanvas = Color(0xFFF7D89B)
internal val MarketCanvasShade = Color(0xFFEABF77)
internal val MarketWood = Color(0xFFD9934E)
internal val MarketWoodLight = Color(0xFFF2BD73)
internal val MarketWoodDark = Color(0xFF81502F)
internal val MarketPaper = Color(0xFFFFF0C9)
internal val MarketInk = Color(0xFF51372B)
internal val MarketCoral = Color(0xFFC94F58)
internal val MarketLeaf = Color(0xFF56854E)

@Immutable
internal data class FruitMergePalette(
    val canvas: Color,
    val canvasShade: Color,
    val wood: Color,
    val woodLight: Color,
    val woodDark: Color,
    val paper: Color,
    val ink: Color,
    val coral: Color,
    val leaf: Color,
    val boardCream: Color,
)

@Composable
internal fun rememberFruitMergePalette(): FruitMergePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.35f
    return remember(scheme, dark) {
        if (dark) {
            FruitMergePalette(
                canvas = scheme.background,
                canvasShade = scheme.surfaceVariant,
                wood = scheme.secondaryContainer,
                woodLight = scheme.surfaceVariant,
                woodDark = scheme.outline,
                paper = scheme.surface,
                ink = scheme.onSurface,
                coral = scheme.primary,
                leaf = scheme.tertiary,
                boardCream = scheme.surface,
            )
        } else {
            FruitMergePalette(
                canvas = scheme.background,
                canvasShade = MarketCanvasShade,
                wood = MarketWood,
                woodLight = MarketWoodLight,
                woodDark = MarketWoodDark,
                paper = MarketPaper,
                ink = MarketInk,
                coral = MarketCoral,
                leaf = MarketLeaf,
                boardCream = Color(0xFFFFF4DF),
            )
        }
    }
}

@Composable
internal fun MarketStallBackground(modifier: Modifier = Modifier) {
    val palette = rememberFruitMergePalette()
    Canvas(modifier.fillMaxSize()) {
        drawRect(palette.canvas)
        drawRect(
            color = palette.canvasShade.copy(alpha = 0.30f),
            topLeft = Offset(0f, size.height * 0.68f),
            size = Size(size.width, size.height * 0.32f),
        )
        val stripeWidth = size.width / 7f
        repeat(7) { index ->
            if (index % 2 == 0) {
                drawRect(
                    color = palette.coral.copy(alpha = 0.08f),
                    topLeft = Offset(index * stripeWidth, 0f),
                    size = Size(stripeWidth, size.height * 0.16f),
                )
            }
        }
        drawLine(
            color = palette.ink.copy(alpha = 0.08f),
            start = Offset(0f, size.height * 0.16f),
            end = Offset(size.width, size.height * 0.16f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
internal fun MarketPriceTag(
    score: Long,
    bestScore: Long,
    bestImprovedInRun: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = rememberFruitMergePalette()
    val state = scoreCardState(bestScore, bestImprovedInRun)
    val scoreLabel = stringResource(Res.string.score)
    val bestLabel = stringResource(Res.string.best_score)
    Surface(
        modifier = modifier.widthIn(min = 164.dp, max = 224.dp),
        shape = MaterialTheme.shapes.medium,
        color = palette.paper,
        contentColor = palette.ink,
        border = BorderStroke(1.5.dp, palette.woodDark.copy(alpha = 0.55f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state != ScoreCardState.BEST_ONLY) {
                PriceValue(scoreLabel, score, palette, Modifier.weight(1f))
            }
            if (state != ScoreCardState.SCORE_ONLY) {
                PriceValue(bestLabel, bestScore, palette, Modifier.weight(1f), crowned = state == ScoreCardState.BEST_ONLY)
            }
        }
    }
}

@Composable
private fun PriceValue(
    label: String,
    value: Long,
    palette: FruitMergePalette,
    modifier: Modifier,
    crowned: Boolean = false,
) {
    Column(
        modifier = modifier.semantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (crowned) "♛ " + label.uppercase() else label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink.copy(alpha = 0.66f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = compactScore(value),
            style = MaterialTheme.typography.titleMedium,
            color = palette.ink,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
internal fun MarketToolButton(
    badge: String,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRotationDegrees: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val palette = rememberFruitMergePalette()
    BadgedBox(
        badge = {
            Badge(containerColor = palette.coral, contentColor = Color.White) {
                Text(badge, fontWeight = FontWeight.Black)
            }
        },
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .size(54.dp)
                .semantics { this.contentDescription = contentDescription },
            shape = MaterialTheme.shapes.large,
            color = if (enabled) palette.woodLight else palette.woodLight.copy(alpha = 0.46f),
            contentColor = palette.ink,
            border = BorderStroke(2.dp, palette.woodDark.copy(alpha = if (enabled) 0.72f else 0.30f)),
            shadowElevation = if (enabled) 3.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.ink,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer { rotationZ = iconRotationDegrees },
                )
            }
        }
    }
}
