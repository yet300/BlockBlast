package ge.yet.game.fruitmerge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.fruitmerge.session.FruitMergeSessionComponent
import ge.yet.game.fruitmerge.session.PaidActionToken
import ge.yet.game.fruitmerge.ui.FruitMergeScreen

@Composable
internal fun FruitMergeContent(
    component: FruitMergeSessionComponent,
    requestClearAd: (PaidActionToken) -> Unit,
    requestShakeAd: (PaidActionToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    FruitMergeScreen(
        component = component.game,
        requestClearAd = requestClearAd,
        requestShakeAd = requestShakeAd,
        modifier = modifier,
    )
}
