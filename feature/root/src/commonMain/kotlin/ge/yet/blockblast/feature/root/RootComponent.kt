package ge.yet.blockblast.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.home.HomeComponent
import kotlinx.coroutines.flow.StateFlow

/**
 * Top-level navigation host. Owns the [ChildStack] and routes between
 * [HomeComponent], [GameComponent]
 *
 * Settings is reachable from BOTH Home and Game by pushing
 */
interface RootComponent : BackHandlerOwner {

    val stack: Value<ChildStack<*, Child>>

    /** Reflects the user's dark-theme preference so [App] can pass it to BlockBlastTheme. */
    val darkTheme: StateFlow<Boolean>

    /** Whether haptic feedback is enabled (mirrors Settings toggle). */
    val vibrationEnabled: StateFlow<Boolean>

    /** Whether sound effects are enabled (mirrors Settings toggle). */
    val soundEnabled: StateFlow<Boolean>

    /** Whether the first-launch tutorial has already been seen / dismissed. */
    val tutorialSeen: StateFlow<Boolean>

    /** Persist that the user has finished the first-launch tutorial. */
    fun onTutorialSeen()

    fun onBackClicked()

    sealed interface Child {
        class Home(val component: HomeComponent) : Child
        class Game(val component: GameComponent) : Child
    }

    /** DI-friendly factory; the concrete impl is created with the Metro graph. */
    fun interface Factory {
        fun create(componentContext: ComponentContext): RootComponent
    }
}
