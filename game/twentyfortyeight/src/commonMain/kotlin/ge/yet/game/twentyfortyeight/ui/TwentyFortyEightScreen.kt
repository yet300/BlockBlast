package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.component.ResultComponent
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.store.UiErrorCode

@Composable
internal fun TwentyFortyEightScreen(
    component: TwentyFortyEightSessionComponent,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()
    val effectState by component.effect.subscribeAsState()
    var error by remember(component) { mutableStateOf<UiErrorCode?>(null) }
    val effect = effectState.effect
    LaunchedEffect(effect?.id) {
        if (effect is TwentyFortyEightSessionComponent.Effect.Error) {
            error = effect.code
        }
    }

    when (val instance = stack.active.instance) {
        is TwentyFortyEightSessionComponent.Child.Playing -> PlayingRoute(
            component = instance.component,
            error = error,
            modifier = modifier.fillMaxSize(),
        )
        is TwentyFortyEightSessionComponent.Child.Result -> ResultRoute(
            component = instance.component,
            error = error,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlayingRoute(
    component: PlayingComponent,
    error: UiErrorCode?,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    val overlay by component.overlay.subscribeAsState()

    PlayingContent(
        model = model,
        onDirection = component::onMove,
        onUndo = component::onUndoRequested,
        onRestart = component::onRestartRequested,
        onStatistics = component::onStatisticsRequested,
        onSkipTutorial = component::onTutorialSkipped,
        modifier = modifier,
        error = error,
    )
    overlay.child?.instance?.let { child -> OverlayContent(child) }
}

@Composable
private fun ResultRoute(
    component: ResultComponent,
    error: UiErrorCode?,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    ResultContent(
        model = model,
        onNewGame = component::onNewGameRequested,
        modifier = modifier,
        error = error,
    )
}
