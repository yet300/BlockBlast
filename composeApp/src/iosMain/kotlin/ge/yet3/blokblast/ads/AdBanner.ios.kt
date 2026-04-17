package ge.yet3.blokblast.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * iOS banner slot. Reserves 50dp of height to match Android so layout is
 * identical across platforms. If `IosAdBridge.makeBannerView` is not wired,
 * renders an empty UIView (still reserved space, no jump).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdBanner(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(50.dp),
        contentAlignment = Alignment.Center,
    ) {
        UIKitView(
            factory = {
                IosAdBridge.makeBannerView?.invoke(AdConfig.HOME_BANNER_UNIT_ID)
                    ?: UIView()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            update = { _ -> },
        )
    }
}
