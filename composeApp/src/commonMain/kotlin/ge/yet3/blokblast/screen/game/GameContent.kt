package ge.yet3.blokblast.screen.game

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.game.GameComponent


@Composable
fun GameContent(component: GameComponent) {
    val model by component.model.subscribeAsState()

    Scaffold(
        content = {
            GameSheet(component = component)
        }
    )
}