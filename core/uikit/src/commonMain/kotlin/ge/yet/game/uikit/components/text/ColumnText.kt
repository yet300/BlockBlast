package ge.yet.game.uikit.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun ColumnText(
    firstText: String,
    secondText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = firstText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )

        Text(
            text = secondText,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 6.dp)
        )

    }
}