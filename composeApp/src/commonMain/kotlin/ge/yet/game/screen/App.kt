package ge.yet.game.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.app.common.config.AppConfig
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.monetization.ads.AdMobConfiguration
import ge.yet.game.monetization.ads.AdMobProvider
import ge.yet.game.monetization.ads.rememberAdMobState
import ge.yet.game.monetization.core.MonetizationEntitlement
import ge.yet.game.uikit.theme.BlockBlastTheme
import ge.yet.game.blockblast.ui.LocalSoundEnabled
import ge.yet.game.blockblast.ui.LocalVibrationEnabled
import ge.yet.game.screen.root.RootContent

@Composable
fun App(rootComponent: RootComponent) {
    val adsEnabled by rootComponent.adsEnabled.collectAsState()
    val monetizationState = rememberAdMobState(
        preferenceEnabled = adsEnabled,
        entitlement = MonetizationEntitlement.FREE,
    )
    val adMobConfiguration = remember {
        AdMobConfiguration(
            bannerAndroidUnitId = AppConfig.BANNER_UNIT_ID_ANDROID,
            bannerIosUnitId = AppConfig.BANNER_UNIT_ID_IOS,
            gameOverInterstitialAndroidUnitId =
                AppConfig.GAME_OVER_INTERSTITIAL_UNIT_ID_ANDROID,
            gameOverInterstitialIosUnitId =
                AppConfig.GAME_OVER_INTERSTITIAL_UNIT_ID_IOS,
        )
    }
    val darkTheme by rootComponent.darkTheme.collectAsState()
    AdMobProvider(
        state = monetizationState,
        configuration = adMobConfiguration,
    ) {
        BlockBlastTheme(darkTheme = darkTheme) {
            val vibrationEnabled by rootComponent.vibrationEnabled.collectAsState()
            val soundEnabled by rootComponent.sfxEnabled.collectAsState()
            CompositionLocalProvider(
                LocalVibrationEnabled provides vibrationEnabled,
                LocalSoundEnabled provides soundEnabled,
            ) {
                RootContent(component = rootComponent)
            }
        }
    }
}
