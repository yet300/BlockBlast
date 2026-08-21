package ge.yet.game.miniapp.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface MiniAppSession {

    @Composable
    fun TopBarContent() = Unit

    @Composable
    fun Content(modifier: Modifier)
}
