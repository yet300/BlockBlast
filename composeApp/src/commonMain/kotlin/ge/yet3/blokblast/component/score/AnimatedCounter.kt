package ge.yet3.blokblast.component.score

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.app.common.utils.formatScore

/**
 * Animated number readout — each digit slides vertically when it changes,
 * inspired by Sina Samaki's "Animated Counter" article.  Non-digit characters
 * (the thousands separator) cross-fade in place.
 *
 * Uses per-position `AnimatedContent` so only the digits that actually change
 * animate; idle positions stay still.  Direction is inferred from the new
 * value vs. the previous one — increases roll up, decreases roll down.
 */
@Composable
fun AnimatedCounter(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = LocalContentColor.current,
    durationMillis: Int = 350,
) {
    val formatted = remember(value) { value.formatScore() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        formatted.forEachIndexed { index, char ->
            // Key by *position from the right* so AnimatedContent slots stay
            // aligned to digit places when the number gains or loses a digit
            // (e.g. 999 → 1,000). Without this key, positional matching makes
            // a ones-place '9' animate into a thousands-place '1' and a tens
            // '9' into a separator ',' — visually jarring and it resets every
            // AnimatedContent's internal Transition.
            val positionFromRight = formatted.length - index
            key(positionFromRight) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val rollingUp = targetState.digitOrNull() != null &&
                            initialState.digitOrNull() != null &&
                            (targetState.digitOrNull()!! > initialState.digitOrNull()!!)
                        val direction = if (rollingUp) 1 else -1
                        val enter = slideInVertically(
                            animationSpec = tween(durationMillis),
                        ) { fullHeight -> direction * fullHeight } + fadeIn(tween(durationMillis))
                        val exit = slideOutVertically(
                            animationSpec = tween(durationMillis),
                        ) { fullHeight -> -direction * fullHeight } + fadeOut(tween(durationMillis))
                        ContentTransform(
                            targetContentEnter = enter,
                            initialContentExit = exit,
                            sizeTransform = SizeTransform(clip = false),
                        )
                    },
                    label = "digit-$positionFromRight",
                ) { displayedChar ->
                    Text(
                        text = displayedChar.toString(),
                        style = style,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun Char.digitOrNull(): Int? = if (this in '0'..'9') this - '0' else null
