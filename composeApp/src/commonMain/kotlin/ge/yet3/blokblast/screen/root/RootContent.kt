package ge.yet3.blokblast.screen.root


import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet3.blokblast.component.utils.cupertinoPredictiveBackAnimation
import ge.yet3.blokblast.screen.game.GameContent
import ge.yet3.blokblast.screen.home.HomeContent

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun RootContent(
    modifier: Modifier = Modifier,
    component: RootComponent
) {
    val childStack by component.stack.subscribeAsState()

    Children(
        modifier = modifier,
        stack = childStack,
        animation = cupertinoPredictiveBackAnimation(
            backHandler = component.backHandler,
            onBack = component::onBackClicked,
        ),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Game -> GameContent(component = instance.component)
            is RootComponent.Child.Home -> HomeContent(component = instance.component)
        }
    }
}