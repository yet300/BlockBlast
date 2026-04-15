package ge.yet.blockblast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import ge.yet3.blokblast.screen.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appGraph = (application as BlockBlastApp).appGraph
        val rootComponent = appGraph.rootFactory.create(
            componentContext = defaultComponentContext()
        )

        setContent {
            App(rootComponent = rootComponent)
        }
    }
}