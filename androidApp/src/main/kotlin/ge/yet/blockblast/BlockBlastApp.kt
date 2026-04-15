package ge.yet.blockblast

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ge.yet3.blokblast.di.AndroidAppGraph
import ge.yet3.blokblast.di.AppGraph

class BlockBlastApp : Application() {

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }
}
