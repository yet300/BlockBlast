package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingScoreState {
    private val _popups = mutableStateListOf<ScorePopup>()
    val popups: List<ScorePopup> get() = _popups

    fun add(points: Long, origin: Offset) {
        _popups.add(ScorePopup(points, origin))
    }

    fun remove(popup: ScorePopup) {
        _popups.remove(popup)
    }
}

data class ScorePopup(val points: Long, val origin: Offset)

@Composable
fun FloatingScoreOverlay(
    state: FloatingScoreState,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        for (popup in state.popups) {
            FloatingScoreItem(
                popup = popup,
                onFinished = { state.remove(popup) }
            )
        }
    }
}

@Composable
private fun FloatingScoreItem(
    popup: ScorePopup,
    onFinished: () -> Unit
) {
    val animAlpha = remember { Animatable(1f) }
    val animY = remember { Animatable(0f) }
    val animScale = remember { Animatable(1f) }

    LaunchedEffect(popup) {
        val duration = 800
        launch {
            animY.animateTo(
                targetValue = -60f,
                animationSpec = tween(duration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            animScale.animateTo(
                targetValue = 1.2f,
                animationSpec = tween(duration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(400)
            animAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
            onFinished()
        }
    }

    Box(
        modifier = Modifier.offset {
            IntOffset(
                x = popup.origin.x.roundToInt(),
                y = popup.origin.y.roundToInt()
            )
        }
    ) {
        Text(
            text = "+${popup.points}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset(y = animY.value.dp)
                .alpha(animAlpha.value)
                .graphicsLayer {
                    scaleX = animScale.value
                    scaleY = animScale.value
                }
        )
    }
}
