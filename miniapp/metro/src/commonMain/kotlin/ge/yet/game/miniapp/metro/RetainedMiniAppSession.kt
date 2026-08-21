package ge.yet.game.miniapp.metro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.miniapp.compose.MiniAppSession

class RetainedMiniAppSession<G : Any>(
    internal val graph: G,
    private val delegate: MiniAppSession,
) : MiniAppSession {
    @Composable
    override fun TopBarContent() = delegate.TopBarContent()

    @Composable
    override fun Content(modifier: Modifier) = delegate.Content(modifier)
}
