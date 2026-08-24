package ge.yet.game.miniapp.metro

import ge.yet.game.miniapp.api.MiniAppId

interface MiniAppRegistryExpectation {
    val expectedIds: Set<MiniAppId>
}
