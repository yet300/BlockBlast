package ge.yet3.blokblast.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet3.blokblast.screen.root.RootContent
import ge.yet3.blokblast.theme.BlockBlastTheme
import ge.yet3.blokblast.theme.LocalSoundEnabled
import ge.yet3.blokblast.theme.LocalVibrationEnabled

@Composable
fun App(rootComponent: RootComponent) {
    val darkTheme by rootComponent.darkTheme.collectAsState()
    BlockBlastTheme(darkTheme = darkTheme) {
        val vibrationEnabled by rootComponent.vibrationEnabled.collectAsState()
        val soundEnabled by rootComponent.soundEnabled.collectAsState()
        CompositionLocalProvider(
            LocalVibrationEnabled provides vibrationEnabled,
            LocalSoundEnabled provides soundEnabled,
        ) {
            RootContent(component = rootComponent)
        }
    }
}