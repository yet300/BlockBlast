package ge.yet.game.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.feature.home.HomeComponent
import ge.yet.game.feature.review.AppReviewComponent
import ge.yet.game.feature.settings.SettingsComponent
import kotlinx.coroutines.flow.StateFlow

/**
 * Top-level navigation host. Owns the [ChildStack] and routes between
 * [HomeComponent], [GameComponent], and [GameResultComponent].
 *
 * Settings is reachable from BOTH Home and Game by pushing
 */
interface RootComponent : BackHandlerOwner {

    val stack: Value<ChildStack<*, Child>>

    val sheetSlot: Value<ChildSlot<*, SheetChild>>

    /** Reflects the user's dark-theme preference so [App] can pass it to BlockBlastTheme. */
    val darkTheme: StateFlow<Boolean>

    /** Whether haptic feedback is enabled (mirrors Settings toggle). */
    val vibrationEnabled: StateFlow<Boolean>

    /** Whether SFX / voice feedback are enabled (mirrors Settings toggle). */
    val sfxEnabled: StateFlow<Boolean>

    /** Whether advertising is enabled by the user. */
    val adsEnabled: StateFlow<Boolean>

    fun onDismissSheet()

    fun onBackClicked()

    sealed interface Child {
        class Home(val component: HomeComponent) : Child
        class Game(val component: GameComponent) : Child
        class Result(val component: GameResultComponent) : Child
    }

    sealed interface SheetChild {
        class Settings(val component: SettingsComponent) : SheetChild
        class AppReview(val component: AppReviewComponent) : SheetChild
    }

    /** DI-friendly factory; the concrete impl is created with the Metro graph. */
    fun interface Factory {
        fun create(componentContext: ComponentContext): RootComponent
    }
}
