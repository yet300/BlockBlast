package ge.yet3.blokblast.component.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
 * Hero best-score card used on the Home screen.  Larger and more elevated
 * than [ScoreChip] — pairs a serif caption with an animated counter rendered
 * in a serif display style.
 */
@Composable
fun BestScoreCard(
    label: String,
    bestScore: Long,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .whisperShadow(shape = shape)
            .ringShadow(
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        AnimatedCounter(
            value = bestScore,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
