package ge.yet.blokblast.data.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.lang.ref.WeakReference

/**
 * Tracks the currently resumed [Activity] so platform components (e.g. the
 * in-app review flow) can reach the foreground Activity without leaking it.
 *
 * Registers itself on construction via the [Application] obtained from the
 * host-provided [android.content.Context].
 */
@SingleIn(AppScope::class)
@Inject
internal class ActivityProvider(context: android.content.Context) {

    private var currentRef: WeakReference<Activity> = WeakReference(null)

    init {
        (context.applicationContext as Application)
            .registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {
                    currentRef = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {
                    if (currentRef.get() === activity) currentRef.clear()
                }
            })
    }

    fun current(): android.app.Activity? = currentRef.get()
}
