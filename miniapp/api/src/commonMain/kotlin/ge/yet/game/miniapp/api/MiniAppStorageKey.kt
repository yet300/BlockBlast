package ge.yet.game.miniapp.api

import kotlin.jvm.JvmInline

@JvmInline
value class MiniAppStorageKey(val value: String)

private val MINI_APP_STORAGE_LOCAL_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*$")

fun MiniAppId.storageKey(localName: String): MiniAppStorageKey {
    requireValid()
    require(MINI_APP_STORAGE_LOCAL_NAME_PATTERN.matches(localName)) {
        "Invalid local mini-app storage key '$localName'; expected lowercase snake_case"
    }
    return MiniAppStorageKey("miniapp.$value.$localName")
}
