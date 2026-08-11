package ge.yet.game.uikit.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp

@Composable
fun RowCard(
    firstText: String,
    secondText: String,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(size = 16.dp))
            .background(MaterialTheme.colorScheme.onTertiary)
            .height(69.dp)
            .padding(vertical = 12.dp, horizontal = 22.5.dp)
    ) {
        Text(
            text = firstText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = secondText,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Right,
        )
    }
}

@PreviewLightDark
@Composable
private fun RowCardPreview() =
    RowCard(
        firstText = "FirstSpec",
        secondText = "SecondSpec"
    )
