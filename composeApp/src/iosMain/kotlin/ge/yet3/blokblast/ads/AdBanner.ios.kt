package ge.yet3.blokblast.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.unit.dp
import com.app.common.config.AppConfig
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * iOS banner slot. Height is 0dp until the ad successfully loads, at which
 * point it expands to 50dp.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdBanner(modifier: Modifier) {
    var isAdLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isAdLoaded) 50.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        UIKitView(
            factory = {
                IosAdBridge.makeBannerView?.invoke(AppConfig.BANNER_UNIT_ID_IOS) {
                    isAdLoaded = true
                } ?: UIView()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            update = { _ -> },
        )
    }
}
