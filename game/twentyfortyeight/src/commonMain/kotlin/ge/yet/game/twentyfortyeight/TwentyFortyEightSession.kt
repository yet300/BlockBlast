package ge.yet.game.twentyfortyeight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
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
    override fun Background(modifier: Modifier) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.background))
    }

    @Composable
    override fun Content(modifier: Modifier) {
        TwentyFortyEightContent(component = component, modifier = modifier)
    }
}
