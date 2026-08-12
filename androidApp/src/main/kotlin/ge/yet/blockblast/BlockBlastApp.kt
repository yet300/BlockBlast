package ge.yet.blockblast

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ge.yet3.blokblast.di.AndroidAppGraph
import ge.yet3.blokblast.di.AppGraph

class BlockBlastApp : Application() {

    val appGraph: AppGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }
}
