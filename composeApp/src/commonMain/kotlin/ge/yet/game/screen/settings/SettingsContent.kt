package ge.yet.game.screen.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.feature.settings.SettingsComponent
import ge.yet.game.screen.settings.content.LibrariesSettingsContent
import ge.yet.game.screen.settings.content.MainSettingsContent
import ge.yet.game.screen.settings.content.MoreSettingsContent
import ge.yet.game.screen.settings.content.DisableAdsContent
import ge.yet.game.screen.settings.content.ResetGameDataContent

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun SettingsContent(
    component: SettingsComponent,
) {
    val childStack by component.stack.subscribeAsState()
    Children(
        modifier = Modifier.animateContentSize(),
        stack = childStack,
        animation = stackAnimation(slide()),
    ) { child ->
        when (val instance = child.instance) {
            is SettingsComponent.Child.Main -> MainSettingsContent(component = instance.component)
            is SettingsComponent.Child.More -> MoreSettingsContent(component = instance.component)
            is SettingsComponent.Child.DisableAds -> DisableAdsContent(component = instance.component)
            is SettingsComponent.Child.Libraries -> LibrariesSettingsContent(component = instance.component)
            is SettingsComponent.Child.ResetGameData -> ResetGameDataContent(component = instance.component)
        }
    }
}
