package ge.yet.game.miniapp.compose

import com.arkivanov.decompose.ComponentContext
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource

interface MiniAppSessionContext {
    val componentContext: ComponentContext
    val visibility: MiniAppVisibilitySource
    val host: MiniAppSessionHost
    val storage: MiniAppStorage
}
