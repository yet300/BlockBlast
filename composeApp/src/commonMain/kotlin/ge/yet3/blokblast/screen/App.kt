package ge.yet3.blokblast.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet.game.uikit.theme.BlockBlastTheme
import ge.yet3.blokblast.ads.AdsManager
import ge.yet3.blokblast.component.utils.LocalAdsEnabled
import ge.yet3.blokblast.component.utils.LocalOnTutorialSeen
import ge.yet3.blokblast.component.utils.LocalSoundEnabled
import ge.yet3.blokblast.component.utils.LocalTutorialSeen
import ge.yet3.blokblast.component.utils.LocalVibrationEnabled
import ge.yet3.blokblast.screen.root.RootContent

@Composable
fun App(rootComponent: RootComponent) {
    val darkTheme by rootComponent.darkTheme.collectAsState()
    BlockBlastTheme(darkTheme = darkTheme) {
        val vibrationEnabled by rootComponent.vibrationEnabled.collectAsState()
        val soundEnabled by rootComponent.sfxEnabled.collectAsState()
        val adsEnabled by rootComponent.adsEnabled.collectAsState()
        var adsRuntimeEnabled by remember { mutableStateOf(false) }
        val tutorialSeen by rootComponent.tutorialSeen.collectAsState()
        val onTutorialSeen = remember(rootComponent) { { rootComponent.onTutorialSeen() } }
        LaunchedEffect(adsEnabled) {
            AdsManager.setEnabled(adsEnabled)
            adsRuntimeEnabled = adsEnabled
        }
        CompositionLocalProvider(
            LocalVibrationEnabled provides vibrationEnabled,
            LocalSoundEnabled provides soundEnabled,
            LocalAdsEnabled provides (adsEnabled && adsRuntimeEnabled),
            LocalTutorialSeen provides tutorialSeen,
            LocalOnTutorialSeen provides onTutorialSeen,
        ) {
            RootContent(component = rootComponent)
        }
    }
}
