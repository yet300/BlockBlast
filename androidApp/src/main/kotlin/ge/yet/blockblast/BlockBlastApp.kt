package ge.yet.blockblast

import android.app.Application
import com.google.android.gms.ads.MobileAds
import dev.zacsweers.metro.createGraphFactory
import ge.yet3.blokblast.di.AndroidAppGraph
import ge.yet3.blokblast.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlockBlastApp : Application() {

    val appGraph: AppGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // MobileAds.initialize does synchronous disk + reflection work and blocks the
        // main thread for 100–400 ms on mid-range devices. Push it to IO so cold start
        // is not stalled; banner AdViews created later will wait on the SDK internally.
        appScope.launch(Dispatchers.IO) {
            MobileAds.initialize(this@BlockBlastApp) { /* no-op — adapter statuses unused */ }
        }
    }
}
