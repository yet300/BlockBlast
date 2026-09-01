package ge.yet.blockblast

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arkivanov.decompose.retainedComponent
import com.google.firebase.Firebase
import com.google.firebase.initialize
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.screen.App

class MainActivity : ComponentActivity() {
    private lateinit var rootComponent: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        Firebase.initialize(this)

        val appGraph = (application as BlockBlastApp).appGraph
        rootComponent = retainedComponent(
            key = "LogicaRoot",
            handleBackButton = true,
            isStateSavingAllowed = { true },
        ) { componentContext ->
            appGraph.rootFactory.create(componentContext = componentContext)
        }

        setContent {
            App(rootComponent = rootComponent)
        }
    }
}
