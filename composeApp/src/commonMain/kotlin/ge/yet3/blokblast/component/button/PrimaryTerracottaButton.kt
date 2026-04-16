package ge.yet3.blokblast.component.button

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ge.yet3.blokblast.component.modifier.ringShadow

@Composable
fun PrimaryTerracottaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .ringShadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                shape = shape,
            ),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
            ),
        )
    }
}
