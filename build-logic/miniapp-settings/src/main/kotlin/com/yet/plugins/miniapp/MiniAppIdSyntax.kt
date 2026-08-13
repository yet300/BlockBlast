package com.yet.plugins.miniapp

internal object MiniAppIdSyntax {
    val pattern = Regex("^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$")

    fun requireValid(value: String) {
        require(pattern.matches(value)) {
            "Invalid mini-app id '$value'; expected namespaced lowercase form such as game.blockblast"
        }
    }
}
