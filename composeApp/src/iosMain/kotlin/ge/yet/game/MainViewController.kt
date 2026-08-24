package ge.yet.game

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.PredictiveBackGestureOverlay
import com.arkivanov.essenty.backhandler.BackDispatcher
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.screen.App

@OptIn(ExperimentalDecomposeApi::class)
fun MainViewController(
    root: RootComponent,
    backDispatcher: BackDispatcher
) = ComposeUIViewController {
    PredictiveBackGestureOverlay(
        backDispatcher = backDispatcher,
        backIcon = { progress, _ ->
        },
    ) {
        App(root)
    }
}