package ge.yet3.blokblast.screen.home

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.home.HomeComponent

@Composable
fun HomeContent(component: HomeComponent) {
    val model by component.model.subscribeAsState()

    Scaffold(
        content = {}
    )
}