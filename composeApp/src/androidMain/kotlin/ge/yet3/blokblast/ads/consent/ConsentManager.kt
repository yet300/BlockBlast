package ge.yet3.blokblast.ads.consent

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google User Messaging Platform (UMP) wrapper.
 *
 * Handles GDPR / regional consent before any AdMob request fires. Call
 * [gather] from the host Activity's `onCreate`. The [onReadyToRequestAds]
 * callback fires exactly once — either after the user finishes the consent
 * form or immediately if consent is not required (or was previously given).
 *
 * The AdMob SDK itself is initialised here, *after* consent, to comply with
 * Google's UMP requirements — initialising earlier risks firing requests
 * before a GDPR-required form has been shown.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    private val mobileAdsInitialized = AtomicBoolean(false)
    private val readyFired = AtomicBoolean(false)

    @Volatile
    private var consentInformation: ConsentInformation? = null

    /**
     * Whether ads can currently be requested. Treat as `false` until
     * [gather] has reported ready at least once in this process.
     */
    fun canRequestAds(context: Context): Boolean {
        val info = consentInformation
            ?: UserMessagingPlatform.getConsentInformation(context).also { consentInformation = it }
        return info.canRequestAds()
    }

    /**
     * Request UMP consent info, show the form if required, and initialise the
     * AdMob SDK once the user can legally receive ads.
     *
     * Safe to call multiple times; the [onReadyToRequestAds] callback fires
     * only on the first transition to "can request ads" per process.
     */
    fun gather(activity: Activity, onReadyToRequestAds: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            // Debug settings can be attached here when testing in the EEA:
            //   .setConsentDebugSettings(ConsentDebugSettings.Builder(activity)
            //       .setDebugGeography(DEBUG_GEOGRAPHY_EEA)
            //       .addTestDeviceHashedId("TEST-DEVICE-HASH").build())
            .build()

        val info = UserMessagingPlatform.getConsentInformation(activity).also {
            consentInformation = it
        }

        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(
                            TAG,
                            "Consent form error ${formError.errorCode}: ${formError.message}",
                        )
                    }
                    if (info.canRequestAds()) fireReady(activity, onReadyToRequestAds)
                }
            },
            { requestError ->
                Log.w(
                    TAG,
                    "Consent info update failed ${requestError.errorCode}: ${requestError.message}",
                )
                // Network / config failure — fall back to whatever the cached
                // consent state allows. Users in non-GDPR regions still get ads.
                if (info.canRequestAds()) fireReady(activity, onReadyToRequestAds)
            },
        )

        // If we already have valid consent from a previous session, fire
        // immediately so the first interstitial can pre-load during the UMP
        // round-trip instead of waiting for it to return.
        if (info.canRequestAds()) fireReady(activity, onReadyToRequestAds)
    }

    private fun fireReady(context: Context, onReadyToRequestAds: () -> Unit) {
        if (mobileAdsInitialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext) { /* no-op */ }
        }
        if (readyFired.compareAndSet(false, true)) {
            onReadyToRequestAds()
        }
    }
}
