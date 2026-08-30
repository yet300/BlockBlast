package ge.yet.game.fruitmerge

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.fruitmerge.session.PaidActionToken

@Composable
internal fun FruitMergeContent(
    component: FruitMergeComponent,
    requestClearAd: (PaidActionToken) -> Unit,
    requestShakeAd: (PaidActionToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val callbacks = Triple(component, requestClearAd, requestShakeAd)
    Box(modifier = modifier)
}
