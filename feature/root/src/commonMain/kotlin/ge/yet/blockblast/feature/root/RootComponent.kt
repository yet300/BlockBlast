package ge.yet.blockblast.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.home.HomeComponent

/**
 * Top-level navigation host. Owns the [ChildStack] and routes between
 * [HomeComponent], [GameComponent]
 *
 * Settings is reachable from BOTH Home and Game by pushing
 */
interface RootComponent : BackHandlerOwner {

    val stack: Value<ChildStack<*, Child>>

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
