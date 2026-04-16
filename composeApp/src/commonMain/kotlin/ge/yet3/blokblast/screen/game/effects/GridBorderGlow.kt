package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import ge.yet3.blokblast.theme.PieceColors
import kotlin.math.sin

@Composable
fun Modifier.gridBorderGlow(comboLevel: Int): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val colors = PieceColors

    drawBehind {
        val dotRadius = 9f
        val spacing = 60f
        
        val mw = size.width
        val mh = size.height

        val perim = 2 * mw + 2 * mh
        val dotCount = (perim / spacing).toInt()
        
        for (i in 0 until dotCount) {
            val dist = i * spacing
            val pX: Float
            val pY: Float
            if (dist < mw) {
                pX = dist
                pY = 0f
            } else if (dist < mw + mh) {
                pX = mw
                pY = dist - mw
            } else if (dist < 2 * mw + mh) {
                pX = mw - (dist - (mw + mh))
                pY = mh
            } else {
                pX = 0f
                pY = mh - (dist - (2 * mw + mh))
            }
            
            val dotPhase = phase + (i.toFloat() / dotCount) * 4 * kotlin.math.PI.toFloat()
            val alpha = (0.3f + 0.7f * (sin(dotPhase) * 0.5f + 0.5f))
            
            val color: androidx.compose.ui.graphics.Color = when {
                comboLevel == 0 -> outlineColor.copy(alpha = alpha * 0.3f)
                comboLevel in 1..3 -> primaryColor.copy(alpha = alpha * 0.7f)
                else -> {
                    val idx = ((i + (phase * 2).toInt()) % colors.size).coerceIn(0, colors.lastIndex)
                    colors[idx].copy(alpha = alpha)
                }
            }
            
            drawCircle(
                color = color,
                radius = dotRadius * (if (comboLevel >= 4) 1.2f else 1f),
                center = androidx.compose.ui.geometry.Offset(pX, pY)
            )
        }
    }
}
