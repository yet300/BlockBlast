package ge.yet.game.fruitmerge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.miniapp.compose.MiniAppSession

class FruitmergeSession internal constructor(
    private val component: FruitmergeComponent,
) : MiniAppSession {
    @Composable
    override fun Content(modifier: Modifier) {
        FruitmergeContent(component = component, modifier = modifier)
    }
}
