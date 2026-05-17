package ge.yet3.blokblast.component.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 *
 * The score value is passed straight through to [AnimatedCounter], which
 * already animates each digit on change via per-position AnimatedContent.
 * A previous version layered an Animatable<Float> tally-rollup on top of
 * that, which forced ScoreChip to recompose ~60×/s for the full duration
 * of each score change — cascading into AnimatedCounter and re-allocating
 * 4-6 AnimatedContent slots per chip per frame. Removing the outer rollup
 * cuts the recomposition count per score event from ~500 to ~1.
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
            value = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = valueColor,
        )
    }
}
