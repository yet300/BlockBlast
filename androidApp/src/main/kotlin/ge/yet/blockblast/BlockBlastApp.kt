package ge.yet.blockblast

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ge.yet3.blokblast.di.AndroidAppGraph
import ge.yet3.blokblast.di.AppGraph

class BlockBlastApp : Application() {

    val appGraph: AppGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }

    // MobileAds.initialize is intentionally NOT called here. Google's UMP
    // requirements mandate that AdMob be initialised only AFTER consent is
    // gathered. Initialisation now happens inside
    // `ConsentManager.fireReady(...)` from MainActivity.
}
