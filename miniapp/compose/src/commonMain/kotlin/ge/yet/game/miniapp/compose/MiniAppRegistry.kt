package ge.yet.game.miniapp.compose

import ge.yet.game.miniapp.api.MiniAppId

interface MiniAppRegistry {

    val manifests: List<MiniAppManifest>

    operator fun get(id: MiniAppId): MiniAppPlugin?
}
