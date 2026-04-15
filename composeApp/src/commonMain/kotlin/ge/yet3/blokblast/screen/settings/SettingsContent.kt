package ge.yet3.blokblast.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.settings.SettingsComponent


@Composable
fun SettingsContent(component: SettingsComponent) {
    val model by component.model.subscribeAsState()

}