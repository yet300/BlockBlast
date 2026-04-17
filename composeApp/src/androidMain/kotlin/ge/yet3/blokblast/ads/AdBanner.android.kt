package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun AdBanner(modifier: Modifier) {
    AdMobBanner(
        adUnitId = AdConfig.HOME_BANNER_UNIT_ID,
        modifier = modifier,
    )
}
