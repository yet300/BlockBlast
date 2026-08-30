package ge.yet.game.fruitmerge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.fruitmerge.session.FruitMergeSessionComponent
import ge.yet.game.fruitmerge.session.PaidActionToken
import ge.yet.game.fruitmerge.ui.FruitMergeScreen
import ge.yet.game.fruitmerge.ui.FruitMergeResultScreen

@Composable
internal fun FruitMergeContent(
    component: FruitMergeSessionComponent,
    requestClearAd: (PaidActionToken) -> Unit,
    requestShakeAd: (PaidActionToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()
    when (val child = stack.active.instance) {
        is FruitMergeSessionComponent.Child.Playing -> FruitMergeScreen(
            component = child.component,
            requestClearAd = requestClearAd,
            requestShakeAd = requestShakeAd,
            modifier = modifier,
        )
        is FruitMergeSessionComponent.Child.Result -> FruitMergeResultScreen(
            component = child.component,
            modifier = modifier,
        )
    }
}
