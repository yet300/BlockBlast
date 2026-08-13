package ge.yet.game.miniapp.compose

import com.arkivanov.decompose.ComponentContext
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource

interface MiniAppPlugin {

    val manifest: MiniAppManifest

    fun createSession(
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
        host: MiniAppSessionHost,
    ): MiniAppSession
}
