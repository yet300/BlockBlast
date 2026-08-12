package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.lexilabs.basic.ads.Consent
import app.lexilabs.basic.ads.DependsOnGoogleUserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow

/** Native Swift marks this gate complete after ATT returns (for any status). */
object IosTrackingAuthorizationBridge {
    internal val completed = MutableStateFlow(false)
    private var requestPending = false

    var requestAuthorization: (() -> Unit)? = null
        set(value) {
            field = value
            if (requestPending) value?.invoke()
        }

    internal fun requestIfNeeded() {
        if (completed.value) return
        requestPending = true
        requestAuthorization?.invoke()
    }

    fun markCompleted() {
        requestPending = false
        completed.value = true
    }
}

@OptIn(DependsOnGoogleUserMessagingPlatform::class)
@Composable
internal actual fun rememberPlatformConsent(): Consent = remember { Consent(null) }

@Composable
internal actual fun rememberTrackingAuthorizationCompleted(adsRequested: Boolean): Boolean {
    val completed by IosTrackingAuthorizationBridge.completed.collectAsState()
    LaunchedEffect(adsRequested) {
        if (adsRequested) IosTrackingAuthorizationBridge.requestIfNeeded()
    }
    return adsRequested && completed
}
