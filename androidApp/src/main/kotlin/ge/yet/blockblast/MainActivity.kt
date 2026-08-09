package ge.yet.blockblast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arkivanov.decompose.defaultComponentContext
import com.google.firebase.Firebase
import com.google.firebase.initialize
import ge.yet3.blokblast.ads.AdsManager
import ge.yet3.blokblast.screen.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Firebase.initialize(this)
        AdsManager.setActivity(this)

        val appGraph = (application as BlockBlastApp).appGraph
        val rootComponent = appGraph.rootFactory.create(
            componentContext = defaultComponentContext()
        )

        setContent {
            App(rootComponent = rootComponent)
        }
    }

    override fun onDestroy() {
        AdsManager.clearActivity(this)
        super.onDestroy()
    }
}
