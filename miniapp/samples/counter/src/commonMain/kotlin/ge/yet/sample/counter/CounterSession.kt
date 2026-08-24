package ge.yet.sample.counter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.compose.MiniAppSession

class CounterSession internal constructor(
    internal val component: CounterComponent,
    internal val host: MiniAppSessionHost,
) : MiniAppSession {
    @Composable
    override fun Content(modifier: Modifier) {
        CounterContent(component = component, modifier = modifier)
    }
}
