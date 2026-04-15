package ge.yet3.blokblast.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet3.blokblast.screen.root.RootContent

@Composable
fun App(
    rootComponent: RootComponent
) {
    MaterialTheme {
        RootContent(component = rootComponent)
    }
}