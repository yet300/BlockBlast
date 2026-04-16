package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CellAnimState {
    val scale = Animatable(1f)
    val alpha = Animatable(1f)
    val flashAlpha = Animatable(0f)
    
    suspend fun popIn(delayMs: Long) {
        scale.snapTo(0.85f)
        alpha.snapTo(1f)
        delay(delayMs)
        scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 800f))
    }
    
    suspend fun clear(delayMs: Long) {
        delay(delayMs)
        coroutineScope {
            launch {
                flashAlpha.animateTo(0.5f, tween(60))
                flashAlpha.animateTo(0f, tween(60))
            }
            launch {
                scale.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            }
            launch {
                alpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            }
        }
    }
    
    suspend fun reset() {
        scale.snapTo(1f)
        alpha.snapTo(1f)
        flashAlpha.snapTo(0f)
    }
}
