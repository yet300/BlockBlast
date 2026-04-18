package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.app.common.config.AppConfig

@Composable
actual fun AdBanner(modifier: Modifier) {
    AdMobBanner(
        adUnitId = AppConfig.BANNER_UNIT_ID_ANDROID,
        modifier = modifier,
    )
}
