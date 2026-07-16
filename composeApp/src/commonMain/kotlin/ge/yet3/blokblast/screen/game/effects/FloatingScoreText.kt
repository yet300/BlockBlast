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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingScoreState {
    private var nextId = 0L

    val popups: List<ScorePopup>
        field = mutableStateListOf<ScorePopup>()

    fun add(points: Long, origin: Offset) {
        popups.add(ScorePopup(id = nextId++, points = points, origin = origin))
    }

    fun remove(popup: ScorePopup) {
        popups.remove(popup)
    }
}

data class ScorePopup(val id: Long, val points: Long, val origin: Offset)

@Composable
fun FloatingScoreOverlay(
    state: FloatingScoreState,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        for (popup in state.popups) {
            key(popup.id) {
                FloatingScoreItem(
                    popup = popup,
                    reducedMotion = reducedMotion,
                    onFinished = { state.remove(popup) }
                )
            }
        }
    }
}

@Composable
private fun FloatingScoreItem(
    popup: ScorePopup,
    reducedMotion: Boolean,
    onFinished: () -> Unit
) {
    val density = LocalDensity.current
    val animAlpha = remember { Animatable(1f) }
    val animY = remember { Animatable(0f) }
    val animScale = remember { Animatable(1f) }

    LaunchedEffect(popup) {
        if (reducedMotion) {
            animAlpha.animateTo(0f, tween(140))
            onFinished()
            return@LaunchedEffect
        }
        val duration = 240
        val travelPx = with(density) { -32.dp.toPx() }
        launch {
            animY.animateTo(
                targetValue = travelPx,
                animationSpec = tween(duration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            animScale.animateTo(
                targetValue = 1.04f,
                animationSpec = tween(duration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(100)
            animAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(140, easing = FastOutSlowInEasing)
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
                .graphicsLayer {
                    translationY = animY.value
                    alpha = animAlpha.value
                    scaleX = animScale.value
                    scaleY = animScale.value
                }
        )
    }
}
