package ge.yet.game.twentyfortyeight

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.miniapp.compose.MiniAppSession

class TwentyFortyEightSession internal constructor(
    private val component: TwentyFortyEightComponent,
) : MiniAppSession {
    @Composable
    override fun Content(modifier: Modifier) {
        TwentyFortyEightContent(component = component, modifier = modifier)
    }
}
