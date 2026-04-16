package ge.yet3.blokblast.screen.game.effects

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.emitter.Emitter
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ConfettiEffect() {
    ConfettiKit(
        modifier = Modifier.fillMaxSize(),
        parties = explode()
    )
}

fun explode(): List<Party> {
    return listOf(
        Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            emitter = Emitter(duration = 100.milliseconds).max(100),
            position = Position.Relative(0.5, 0.5)
        )
    )
}
