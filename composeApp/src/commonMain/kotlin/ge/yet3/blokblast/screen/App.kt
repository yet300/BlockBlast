package ge.yet3.blokblast.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet3.blokblast.screen.root.RootContent
import ge.yet3.blokblast.theme.BlockBlastTheme

@Composable
fun App(rootComponent: RootComponent) {
    val darkTheme by rootComponent.darkTheme.collectAsState()
    BlockBlastTheme(darkTheme = darkTheme) {
        RootContent(component = rootComponent)
    }
}