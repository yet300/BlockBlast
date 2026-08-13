package com.yet.plugins.miniapp

internal data class MiniAppDependencyViolation(
    val projectPath: String,
    val configuration: String,
    val dependencyPath: String,
    val replacement: String,
) {
    fun message(): String = "$projectPath: $configuration may not depend on $dependencyPath; use $replacement"
}

internal object MiniAppDependencyBoundary {
    private val allowedMainProjects = setOf(
        ":miniapp:api", ":miniapp:compose", ":miniapp:metro", ":core:common", ":core:domain",
        ":core:uikit", ":monetization:core",
    )
    private val forbiddenPrefixes = setOf(":feature:", ":game:", ":miniapp:samples:")
    private val forbiddenExact = setOf(":composeApp", ":androidApp", ":monetization:ads")

    fun violationFor(projectPath: String, configuration: String, dependencyPath: String): MiniAppDependencyViolation? {
        if (dependencyPath == projectPath) return null
        if (dependencyPath == ":miniapp:testkit" && configuration.contains("test", ignoreCase = true)) return null
        if (dependencyPath in allowedMainProjects) return null
        if (dependencyPath == ":miniapp:testkit") return violation(projectPath, configuration, dependencyPath, ":miniapp:api")
        if (dependencyPath == ":monetization:ads") return violation(projectPath, configuration, dependencyPath, ":miniapp:compose MiniAppInterstitialCapability")
        if (dependencyPath in forbiddenExact || forbiddenPrefixes.any(dependencyPath::startsWith)) {
            return violation(projectPath, configuration, dependencyPath, replacementFor(dependencyPath))
        }
        return violation(projectPath, configuration, dependencyPath, ":miniapp:api or a stable :core contract")
    }

    private fun violation(project: String, configuration: String, dependency: String, replacement: String) =
        MiniAppDependencyViolation(project, configuration, dependency, replacement)

    private fun replacementFor(path: String): String = when {
        path.startsWith(":feature:") -> ":miniapp:compose"
        path.startsWith(":game:") || path.startsWith(":miniapp:samples:") -> ":miniapp:api"
        else -> ":miniapp:compose or a stable :core contract"
    }
}
