package ge.yet.blockblast.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import ge.yet.blockblast.feature.settings.libraries.LibrariesSettingsComponent
import ge.yet.blockblast.feature.settings.main.MainSettingsComponent
import ge.yet.blockblast.feature.settings.more.MoreSettingsComponent

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
        class Libraries(val component: LibrariesSettingsComponent) : Child
    }

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            onBackClicked: () -> Unit,
        ): SettingsComponent
    }
}
