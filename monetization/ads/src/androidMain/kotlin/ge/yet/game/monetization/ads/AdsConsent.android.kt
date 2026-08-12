package ge.yet.game.monetization.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.lexilabs.basic.ads.Consent
import app.lexilabs.basic.ads.DependsOnGoogleUserMessagingPlatform
import app.lexilabs.basic.ads.getActivity

@OptIn(DependsOnGoogleUserMessagingPlatform::class)
@Composable
internal actual fun rememberPlatformConsent(): Consent {
    val activity = LocalContext.current.getActivity()
    return remember(activity) { Consent(activity) }
}

@Composable
internal actual fun rememberTrackingAuthorizationCompleted(adsRequested: Boolean): Boolean = adsRequested
