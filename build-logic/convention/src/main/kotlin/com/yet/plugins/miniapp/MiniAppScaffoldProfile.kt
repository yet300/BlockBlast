package com.yet.plugins.miniapp

internal enum class MiniAppScaffoldProfile {
    BASIC,
    GAME,
    ;

    companion object {
        fun from(raw: String?): MiniAppScaffoldProfile = when (raw?.trim()?.lowercase()) {
            null, "", "basic" -> BASIC
            "game" -> GAME
            else -> throw IllegalArgumentException(
                "Unknown miniAppProfile '$raw'; expected basic or game",
            )
        }
    }
}
