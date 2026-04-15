package ge.yet3.blokblast.screen

import androidx.compose.runtime.Composable
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet3.blokblast.screen.root.RootContent
import ge.yet3.blokblast.theme.BlockBlastTheme

@Composable
fun App(rootComponent: RootComponent) {
    BlockBlastTheme {
        RootContent(component = rootComponent)
    }
}