package com.yet.plugins.miniapp

internal object MiniAppResourcePackage {
    private val segment = Regex("^[a-z][a-z0-9]*$")

    fun from(projectPath: String): String {
        val segments = projectPath.split(':').filter(String::isNotBlank)
        require(segments.isNotEmpty() && segments.all(segment::matches)) {
            "Cannot derive mini-app resource package from '$projectPath'"
        }
        return "ge.yet.${segments.joinToString(".")}.generated.resources"
    }
}
