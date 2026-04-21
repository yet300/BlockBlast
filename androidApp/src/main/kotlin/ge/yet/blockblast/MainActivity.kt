package ge.yet.blockblast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import com.google.firebase.Firebase
import com.google.firebase.initialize
import ge.yet3.blokblast.ads.consent.ConsentManager
import ge.yet3.blokblast.screen.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Firebase.initialize(this)

        // Kick off UMP consent gathering. `ConsentManager` will initialise
        // MobileAds internally once consent permits ad requests — all
        // interstitial / banner loads gate on `ConsentManager.canRequestAds`,
        // so this is the single source of truth for when ads may fire.
        ConsentManager.gather(this) { /* ready — loads will succeed from here */ }

        val appGraph = (application as BlockBlastApp).appGraph
        val rootComponent = appGraph.rootFactory.create(
            componentContext = defaultComponentContext()
        )

        setContent {
            App(rootComponent = rootComponent)
        }
    }
}