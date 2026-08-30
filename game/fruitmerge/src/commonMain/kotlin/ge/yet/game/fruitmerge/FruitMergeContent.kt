package ge.yet.game.fruitmerge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.fruitmerge.session.PaidActionToken
import ge.yet.game.fruitmerge.ui.FruitMergeScreen

@Composable
internal fun FruitMergeContent(
    component: FruitMergeComponent,
    requestClearAd: (PaidActionToken) -> Unit,
    requestShakeAd: (PaidActionToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    FruitMergeScreen(
        component = component,
        requestClearAd = requestClearAd,
        requestShakeAd = requestShakeAd,
        modifier = modifier,
    )
}
