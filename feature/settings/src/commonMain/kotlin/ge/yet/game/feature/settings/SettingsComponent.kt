package ge.yet.game.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import ge.yet.game.feature.settings.disableads.DisableAdsComponent
import ge.yet.game.feature.settings.libraries.LibrariesSettingsComponent
import ge.yet.game.feature.settings.main.MainSettingsComponent
import ge.yet.game.feature.settings.more.MoreSettingsComponent
import ge.yet.game.feature.settings.reset.ResetGameDataComponent
import ge.yet.game.miniapp.api.MiniAppDataResetResult

/**
 * Settings screen. Reachable from BOTH Home and Game via Root navigation.
 * Hosts a ChildStack of [Child.Main] (toggles) and [Child.More] (links and
 * open-source libraries).
 */
interface SettingsComponent : BackHandlerOwner {

    val stack: Value<ChildStack<*, Child>>

    fun onBackClicked()

    sealed interface Child {
        class Main(val component: MainSettingsComponent) : Child
        class More(val component: MoreSettingsComponent) : Child
        class DisableAds(val component: DisableAdsComponent) : Child
        class Libraries(val component: LibrariesSettingsComponent) : Child
        class ResetGameData(val component: ResetGameDataComponent) : Child
    }

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            clearGameData: suspend () -> MiniAppDataResetResult,
            onBackClicked: () -> Unit,
        ): SettingsComponent
    }
}
