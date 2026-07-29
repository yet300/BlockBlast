package ge.yet3.blokblast.screen.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.common.utils.formatScore
import ge.yet.blockblast.feature.game.result.GameResultComponent
import ge.yet3.blokblast.component.button.SecondaryWarmSandButton
import ge.yet3.blokblast.component.modifier.ringShadow
import ge.yet3.blokblast.component.modifier.whisperShadow

@Composable
internal fun ResultCard(
    model: GameResultComponent.Model,
    scoreLabel: String,
    bestLabel: String,
    newBestLabel: String,
    continueLabel: String,
    newGameLabel: String,
    homeLabel: String,
    onPrimaryClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .whisperShadow(shape = shape, elevation = 24.dp)
            .ringShadow(
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = scoreLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = model.snapshot.score.formatScore(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (model.snapshot.isNewBest) {
                "$newBestLabel · ${model.snapshot.bestScore.formatScore()}"
            } else {
                "$bestLabel · ${model.snapshot.bestScore.formatScore()}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (model.snapshot.isNewBest) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (model.snapshot.isNewBest) FontWeight.SemiBold else FontWeight.Normal,
        )

        Spacer(Modifier.height(24.dp))

        ResultPrimaryButton(
            text = if (model.isContinuePhase) {
                "$continueLabel (${model.continueSecondsRemaining})"
            } else {
                newGameLabel
            },
            showAdIcon = model.isContinuePhase,
            onClick = onPrimaryClicked,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        SecondaryWarmSandButton(
            text = homeLabel,
            onClick = onHomeClicked,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultPrimaryButton(
    text: String,
    showAdIcon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAdIcon) {
                AdPlayIcon(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
            )
        }
    }
}

@Composable
private fun AdPlayIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(size.minDimension * 0.18f),
            style = Stroke(width = strokeWidth),
        )
        val triangle = Path().apply {
            moveTo(size.width * 0.42f, size.height * 0.30f)
            lineTo(size.width * 0.72f, size.height * 0.50f)
            lineTo(size.width * 0.42f, size.height * 0.70f)
            close()
        }
        drawPath(path = triangle, color = color)
    }
}
