package ge.yet3.blokblast.component.score

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ge.yet3.blokblast.component.modifier.ringShadow
import ge.yet3.blokblast.component.modifier.whisperShadow

/**
 * Compact pill that pairs a small caption label with an animated number.
 * Used in the Game top bar for live score / best-score readouts.
 */
@Composable
fun ScoreChip(
    label: String,
    value: Long,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // Tally rollup — interpolate from the previous score to the new one so
    // intermediate digits actually flow through (5000 → 5100 → 5200 → ... →
    // 5400) instead of swapping leftmost digits in one step. Duration scales
    // with the size of the jump but is capped so big bonuses don't crawl.
    val animated = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) {
        val delta = kotlin.math.abs(value - animated.value.toLong())
        val durationMs = (200 + delta.toInt() * 2).coerceIn(200, 800)
        animated.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        )
    }
    val displayValue = animated.value.toLong()

    Column(
        modifier = modifier
            .whisperShadow(shape = shape)
            .ringShadow(
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = labelColor,
        )
        AnimatedCounter(
            value = displayValue,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = valueColor,
        )
    }
}
