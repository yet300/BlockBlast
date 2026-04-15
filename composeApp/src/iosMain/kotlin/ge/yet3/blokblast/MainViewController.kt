package ge.yet3.blokblast

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.PredictiveBackGestureOverlay
import com.arkivanov.essenty.backhandler.BackDispatcher
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet3.blokblast.screen.App

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