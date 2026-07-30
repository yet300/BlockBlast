package ge.yet3.blokblast.ads

import android.app.Activity
import ge.yet3.blokblast.ads.consent.ConsentManager
import java.lang.ref.WeakReference

actual object AdsManager {
    private var activityRef: WeakReference<Activity>? = null
    private var adsEnabled: Boolean = false

    actual val enabled: Boolean
        get() = adsEnabled

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        if (adsEnabled) requestConsentAndAds()
    }

    fun clearActivity(activity: Activity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    actual fun setEnabled(enabled: Boolean) {
        if (adsEnabled == enabled) return
        adsEnabled = enabled
        if (enabled) requestConsentAndAds()
    }

    actual fun requestConsentAndAds() {
        if (!adsEnabled) return
        val activity = activityRef?.get()
        if (activity != null) {
            ConsentManager.gather(activity) {
                // Done
            }
        }
    }
}
