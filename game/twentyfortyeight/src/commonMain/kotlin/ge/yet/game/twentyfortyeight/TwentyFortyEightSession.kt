package ge.yet.game.twentyfortyeight

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent

class TwentyFortyEightSession internal constructor(
    internal val component: TwentyFortyEightSessionComponent,
) : MiniAppSession {
    override val frameMode: Value<MiniAppFrameMode> = component.frameMode

    override fun handleBack(): Boolean = component.handleBack()

    @Composable
    override fun Content(modifier: Modifier) {
        TwentyFortyEightContent(component = component, modifier = modifier)
    }
}
