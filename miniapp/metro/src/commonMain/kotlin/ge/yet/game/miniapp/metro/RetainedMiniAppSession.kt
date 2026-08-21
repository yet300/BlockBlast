package ge.yet.game.miniapp.metro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.miniapp.compose.MiniAppSession

class RetainedMiniAppSession<G : Any>(
    internal val graph: G,
    private val delegate: MiniAppSession,
) : MiniAppSession {
    override val frameMode: Value<MiniAppFrameMode> = delegate.frameMode

    @Composable
    override fun TopBarContent() = delegate.TopBarContent()

    @Composable
    override fun Content(modifier: Modifier) = delegate.Content(modifier)
}
