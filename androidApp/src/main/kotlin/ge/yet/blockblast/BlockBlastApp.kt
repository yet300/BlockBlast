package ge.yet.blockblast

import android.app.Application
import com.google.android.gms.ads.MobileAds
import dev.zacsweers.metro.createGraphFactory
import ge.yet3.blokblast.di.AndroidAppGraph
import ge.yet3.blokblast.di.AppGraph

class BlockBlastApp : Application() {

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AndroidAppGraph.Factory>().create(this)

        // Initialise the Google Mobile Ads SDK once per process, as early as
        // possible, so banner AdViews created by Compose can start their ad
        // requests without waiting for the interstitial composable to run.
        MobileAds.initialize(this) { /* no-op — adapter statuses unused */ }
    }
}
