package ge.yet.game.miniapp.testkit

import com.arkivanov.decompose.ComponentContext
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppSessionContext

class TestMiniAppSessionContext(
    override val componentContext: ComponentContext,
    override val visibility: MiniAppVisibilitySource,
    override val host: MiniAppSessionHost,
    override val storage: MiniAppStorage = NoopMiniAppStorage,
) : MiniAppSessionContext
