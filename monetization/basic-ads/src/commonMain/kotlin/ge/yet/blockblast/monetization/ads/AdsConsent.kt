package ge.yet.blockblast.monetization.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lexilabs.basic.ads.Consent
import app.lexilabs.basic.ads.DependsOnGoogleUserMessagingPlatform

@DependsOnGoogleUserMessagingPlatform
@Composable
internal expect fun rememberPlatformConsent(): Consent

@Composable
internal expect fun rememberTrackingAuthorizationCompleted(adsRequested: Boolean): Boolean

@OptIn(DependsOnGoogleUserMessagingPlatform::class)
@Composable
internal fun rememberAdsConsentAllowsRequests(adsRequested: Boolean): Boolean {
    val consent = rememberPlatformConsent()
    val trackingAuthorizationCompleted = rememberTrackingAuthorizationCompleted(adsRequested)
    var consentAllowsRequests by remember(consent) { mutableStateOf(false) }

    LaunchedEffect(consent, adsRequested, trackingAuthorizationCompleted) {
        if (!adsRequested || !trackingAuthorizationCompleted) {
            consentAllowsRequests = false
            return@LaunchedEffect
        }

        fun publishCurrentConsent() {
            consentAllowsRequests = consent.canRequestAds
        }

        consent.requestConsentInfoUpdate(
            onCompletion = {
                consent.loadAndShowConsentForm(
                    onLoaded = ::publishCurrentConsent,
                    onError = { publishCurrentConsent() },
                )
            },
            onError = { publishCurrentConsent() },
        )
        publishCurrentConsent()
    }

    return consentAllowsRequests
}
