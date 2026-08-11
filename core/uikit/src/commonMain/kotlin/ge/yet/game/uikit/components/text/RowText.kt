package ge.yet.game.uikit.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun RowText(
    firstText: String,
    secondText: String,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = firstText,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = secondText,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Right,
        )
    }
}