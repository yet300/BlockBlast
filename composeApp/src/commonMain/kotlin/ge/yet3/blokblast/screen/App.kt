package ge.yet3.blokblast.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.DependsOnGoogleUserMessagingPlatform
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet.game.uikit.theme.BlockBlastTheme
import ge.yet3.blokblast.ads.rememberAdsConsentAllowsRequests
import ge.yet3.blokblast.ads.shouldRequestAds
import ge.yet3.blokblast.component.utils.LocalAdsEnabled
import ge.yet3.blokblast.component.utils.LocalOnTutorialSeen
import ge.yet3.blokblast.component.utils.LocalSoundEnabled
import ge.yet3.blokblast.component.utils.LocalTutorialSeen
import ge.yet3.blokblast.component.utils.LocalVibrationEnabled
import ge.yet3.blokblast.screen.root.RootContent

@OptIn(DependsOnGoogleMobileAds::class, DependsOnGoogleUserMessagingPlatform::class)
@Composable
fun App(rootComponent: RootComponent) {
    val adsEnabled by rootComponent.adsEnabled.collectAsState()
    val consentAllowsRequests = rememberAdsConsentAllowsRequests(adsRequested = adsEnabled)
    val canRequestAds = shouldRequestAds(
        preferenceEnabled = adsEnabled,
        consentAllowsRequests = consentAllowsRequests,
    )
    val darkTheme by rootComponent.darkTheme.collectAsState()
    BlockBlastTheme(darkTheme = darkTheme) {
        val vibrationEnabled by rootComponent.vibrationEnabled.collectAsState()
        val soundEnabled by rootComponent.sfxEnabled.collectAsState()
        val tutorialSeen by rootComponent.tutorialSeen.collectAsState()
        val onTutorialSeen = remember(rootComponent) { { rootComponent.onTutorialSeen() } }

        if (canRequestAds) {
            BasicAds.Initialize()
        }
        CompositionLocalProvider(
            LocalVibrationEnabled provides vibrationEnabled,
            LocalSoundEnabled provides soundEnabled,
            LocalAdsEnabled provides canRequestAds,
            LocalTutorialSeen provides tutorialSeen,
            LocalOnTutorialSeen provides onTutorialSeen,
        ) {
            RootContent(component = rootComponent)
        }
    }
}
