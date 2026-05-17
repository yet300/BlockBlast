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
 * iOS banner slot.
 *
 * Three-state visibility model:
 *   - Initial render: slot reserves 50dp so the underlying GADBannerView has
 *     non-zero bounds at `.load()` time. Without that, the iOS SDK rejects
 *     every request before the network with "Invalid ad width or height"
 *     and the slot would stay collapsed forever (chicken-and-egg).
 *   - Load succeeds: stays at 50dp.
 *   - Load fails: collapses to 0dp so an empty banner-sized hole isn't shown
 *     when offline / no-fill. If a later refresh succeeds, the slot expands
 *     back to 50dp on the `onLoaded` callback.
 *
 * Android does the same logical thing in its AdMobBanner, but can start at
 * 0dp because Android's AdView falls back to its declared AdSize.BANNER
 * intrinsic size when its container is collapsed. The iOS SDK reads the
 * GADBannerView's bounds directly and is stricter.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdBanner(modifier: Modifier) {
    var isAdLoaded by remember { mutableStateOf(false) }
    var hasFailed by remember { mutableStateOf(false) }

    // Reserve 50dp unless we've seen a failure with no successful load yet.
    val visible = isAdLoaded || !hasFailed

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (visible) 50.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        UIKitView(
            factory = {
                IosAdBridge.makeBannerView?.invoke(
                    AppConfig.BANNER_UNIT_ID_IOS,
                    {
                        isAdLoaded = true
                        hasFailed = false
                    },
                    {
                        hasFailed = true
                    },
                ) ?: UIView()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            update = { _ -> },
        )
    }
}
