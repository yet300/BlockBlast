package ge.yet.game.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.review.AppReviewComponent
import ge.yet.game.feature.settings.SettingsComponent
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppSession
import kotlinx.coroutines.flow.StateFlow

interface RootComponent : BackHandlerOwner {
    val stack: Value<ChildStack<*, Child>>
    val sheetSlot: Value<ChildSlot<*, SheetChild>>
    val darkTheme: StateFlow<Boolean>
    val adsEnabled: StateFlow<Boolean>

    fun onBackClicked()
    fun onSettingsClicked()
    fun onDismissSheet()

    sealed interface Child {
        class Catalog(val component: CatalogComponent) : Child
        class RunningMiniApp(
            val id: MiniAppId,
            val state: MiniAppState,
        ) : Child
    }

    sealed interface MiniAppState {
        class Content(val session: MiniAppSession) : MiniAppState
        data class Unavailable(val id: MiniAppId) : MiniAppState
    }

    sealed interface SheetChild {
        class Settings(val component: SettingsComponent) : SheetChild
        class AppReview(val component: AppReviewComponent) : SheetChild
    }

    fun interface Factory {
        fun create(componentContext: ComponentContext): RootComponent
    }
}
