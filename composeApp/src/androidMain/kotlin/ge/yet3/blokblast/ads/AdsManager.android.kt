package ge.yet3.blokblast.ads

import android.app.Activity
import ge.yet3.blokblast.ads.consent.ConsentManager
import java.lang.ref.WeakReference

actual object AdsManager {
    private var activityRef: WeakReference<Activity>? = null

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    actual fun requestConsentAndAds() {
        val activity = activityRef?.get()
        if (activity != null) {
            ConsentManager.gather(activity) {
                // Done
            }
        }
    }
}
