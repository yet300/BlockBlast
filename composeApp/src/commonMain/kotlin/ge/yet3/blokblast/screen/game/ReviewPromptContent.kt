package ge.yet3.blokblast.screen.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.review_prompt_body
import blockblast.composeapp.generated.resources.review_prompt_dont_show
import blockblast.composeapp.generated.resources.review_prompt_leave_feedback
import blockblast.composeapp.generated.resources.review_prompt_title
import ge.yet.blockblast.feature.game.reviewprompt.ReviewPromptComponent
import ge.yet3.blokblast.component.button.PrimaryTerracottaButton
import ge.yet3.blokblast.component.button.SecondaryWarmSandButton
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReviewPromptContent(component: ReviewPromptComponent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnthropicMark()
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(Res.string.review_prompt_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.review_prompt_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryTerracottaButton(
                text = stringResource(Res.string.review_prompt_leave_feedback),
                onClick = component::onLeaveFeedbackClicked,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryWarmSandButton(
                text = stringResource(Res.string.review_prompt_dont_show),
                onClick = component::onDontShowAgainClicked,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AnthropicMark() {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val stroke = 2.4.dp.toPx()
            drawLine(
                color = ink,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ink,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ink,
                start = Offset(size.width * 0.16f, size.height * 0.16f),
                end = Offset(size.width * 0.84f, size.height * 0.84f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ink,
                start = Offset(size.width * 0.84f, size.height * 0.16f),
                end = Offset(size.width * 0.16f, size.height * 0.84f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
