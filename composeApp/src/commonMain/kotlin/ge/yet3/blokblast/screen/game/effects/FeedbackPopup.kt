package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.feedback_combo
import blockblast.composeapp.generated.resources.feedback_good
import blockblast.composeapp.generated.resources.feedback_great
import blockblast.composeapp.generated.resources.feedback_perfect
import ge.yet.blokblast.domain.model.FeedbackType
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

class FeedbackPopupState {
    private val _popups = mutableStateListOf<FeedbackItem>()
    val popups: List<FeedbackItem> get() = _popups

    fun add(type: FeedbackType?, comboLevel: Int?) {
        _popups.add(FeedbackItem(type, comboLevel, Clock.System.now().toEpochMilliseconds()))
    }

    fun remove(item: FeedbackItem) {
        _popups.remove(item)
    }
}

data class FeedbackItem(val type: FeedbackType?, val comboLevel: Int?, val timestamp: Long)

@Composable
fun FeedbackPopupOverlay(
    state: FeedbackPopupState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        for (item in state.popups) {
            FeedbackPopupItem(item = item, onFinished = { state.remove(item) })
        }
    }
}

@Composable
private fun FeedbackPopupItem(
    item: FeedbackItem,
    onFinished: () -> Unit
) {
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(item) {
        visible.value = true
        delay(1200)
        visible.value = false
        delay(300) // wait for exit animation
        onFinished()
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f, animationSpec = tween(200)) + fadeOut(tween(200))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.comboLevel != null && item.comboLevel >= 2) {
                ComboText(item.comboLevel)
            } else if (item.type != null) {
                FeedbackText(item.type)
                if (item.type == FeedbackType.PERFECT) {
                    ConfettiEffect()
                }
            }
        }
    }
}

@Composable
private fun ComboText(level: Int) {
    val comboStr = stringResource(Res.string.feedback_combo)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
            append("$comboStr ")
        }
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)) {
            append("$level")
        }
    }

    // Shadow effect by drawing twice
    Text(
        text = text,
        modifier = Modifier.offset(x = 2.dp, y = 2.dp),
        style = TextStyle(color = MaterialTheme.colorScheme.surface)
    )
    Text(text = text)
}

@Composable
private fun FeedbackText(type: FeedbackType) {
    val text = when (type) {
        FeedbackType.GOOD -> stringResource(Res.string.feedback_good)
        FeedbackType.GREAT -> stringResource(Res.string.feedback_great)
        FeedbackType.PERFECT -> stringResource(Res.string.feedback_perfect)
    }

    val brush = if (type == FeedbackType.PERFECT) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary
            )
        )
    } else null

    val style = MaterialTheme.typography.displayMedium.copy(
        fontWeight = FontWeight.ExtraBold
    )

    if (brush != null) {
        Text(
            text = text,
            style = style.copy(brush = brush)
        )
    } else {
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
