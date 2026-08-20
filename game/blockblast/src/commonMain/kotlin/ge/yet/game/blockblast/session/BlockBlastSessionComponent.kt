package ge.yet.game.blockblast.session

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource

internal interface BlockBlastSessionComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        class Playing(val component: GameComponent) : Child
        class Result(val component: GameResultComponent) : Child
    }

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            visibility: MiniAppVisibilitySource,
            host: MiniAppSessionHost,
        ): BlockBlastSessionComponent
    }
}
