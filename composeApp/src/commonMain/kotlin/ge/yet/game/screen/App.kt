package ge.yet.game.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.app.common.config.AppConfig
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.monetization.ads.AdMobConfiguration
import ge.yet.game.monetization.ads.AdMobProvider
import ge.yet.game.monetization.ads.rememberAdMobState
import ge.yet.game.monetization.core.MonetizationEntitlement
import ge.yet.game.screen.root.RootContent
import ge.yet.game.uikit.theme.LogicaTheme

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
        LogicaTheme(darkTheme = darkTheme) {
            RootContent(component = rootComponent)
        }
    }
}
