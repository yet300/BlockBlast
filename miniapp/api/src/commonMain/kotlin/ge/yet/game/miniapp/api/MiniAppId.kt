package ge.yet.game.miniapp.api

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MiniAppId(val value: String)

private val MINI_APP_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$")

fun MiniAppId.isValid(): Boolean = MINI_APP_ID_PATTERN.matches(value)

fun MiniAppId.requireValid(): MiniAppId = apply {
    require(isValid()) {
        "Invalid mini-app id '$value'; expected namespaced lowercase form such as game.blockblast"
    }
}
