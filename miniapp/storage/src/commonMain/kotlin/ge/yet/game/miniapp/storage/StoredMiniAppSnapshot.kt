package ge.yet.game.miniapp.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class StoredMiniAppSnapshot(
    val version: Int,
    val payload: JsonElement,
)
