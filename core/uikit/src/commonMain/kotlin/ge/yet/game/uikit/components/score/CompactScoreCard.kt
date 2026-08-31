package ge.yet.game.uikit.components.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CompactScoreCard(
    primaryLabel: String,
    primaryValue: Long,
    secondaryLabel: String,
    secondaryValue: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 64.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactScoreMetric(
                label = primaryLabel,
                value = primaryValue,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.height(34.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            CompactScoreMetric(
                label = secondaryLabel,
                value = secondaryValue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompactScoreMetric(
    label: String,
    value: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label $value"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = compactScore(value),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

fun compactScore(value: Long): String {
    val safeValue = value.coerceAtLeast(0)
    val (divisor, suffix) = when {
        safeValue >= TRILLION -> TRILLION to "T"
        safeValue >= BILLION -> BILLION to "B"
        safeValue >= MILLION -> MILLION to "M"
        safeValue >= THOUSAND -> THOUSAND to "K"
        else -> return safeValue.toString()
    }
    val whole = safeValue / divisor
    val decimal = (safeValue % divisor) / (divisor / 10)
    return if (decimal == 0L) "$whole$suffix" else "$whole.$decimal$suffix"
}

private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
private const val BILLION = 1_000_000_000L
private const val TRILLION = 1_000_000_000_000L
