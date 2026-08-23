package com.yet.plugins.miniapp

internal data class MiniAppDependencyViolation(
    val projectPath: String,
    val configuration: String,
    val dependencyPath: String,
    val replacement: String,
) {
    fun message(): String = "$projectPath: $configuration may not depend on $dependencyPath; use $replacement"
}

internal data class MiniAppSourceImportViolation(
    val projectPath: String,
    val sourcePath: String,
    val importPath: String,
    val replacement: String,
) {
    fun message(): String = "$projectPath: $sourcePath may not import $importPath; use $replacement"
}

internal object MiniAppDependencyBoundary {
    private val allowedMainProjects = setOf(
        ":miniapp:api", ":miniapp:compose", ":miniapp:metro", ":miniapp:audio", ":miniapp:audio-presets",
        ":core:common", ":core:domain",
        ":core:uikit", ":core:pattern", ":monetization:core",
    )
    private val forbiddenPrefixes = setOf(":feature:", ":game:", ":miniapp:samples:")
    private val forbiddenExact = setOf(":composeApp", ":androidApp", ":monetization:ads")
    private val forbiddenPlatformAudioImportPrefixes = setOf(
        "android.media.",
        "androidx.media3.",
        "platform.AVFAudio.",
        "platform.AudioToolbox.",
        "platform.CoreAudio.",
        "platform.CoreAudioTypes.",
        "platform.MediaToolbox.",
    )
    private val externalAudioTokens = setOf("audio", "sound", "media3", "oboe", "klang", "korau")
    private const val AUDIO_REPLACEMENT = "MiniAppSessionContext.audio and :miniapp:audio-presets"

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

    fun externalViolationFor(
        projectPath: String,
        configuration: String,
        group: String?,
        name: String,
    ): MiniAppDependencyViolation? {
        if (group == "com.russhwolf" && name.startsWith("multiplatform-settings")) {
            val replacement = if (configuration.contains("test", ignoreCase = true)) {
                "MiniAppStorage test fixtures"
            } else {
                "MiniAppStorage"
            }
            return violation(projectPath, configuration, "$group:$name", replacement)
        }
        val coordinate = "${group.orEmpty()}:$name"
        val normalized = coordinate.lowercase()
        if (externalAudioTokens.none { token -> token in normalized }) return null
        return violation(projectPath, configuration, coordinate, AUDIO_REPLACEMENT)
    }

    fun sourceImportViolationFor(
        projectPath: String,
        sourcePath: String,
        importPath: String,
    ): MiniAppSourceImportViolation? {
        if (forbiddenPlatformAudioImportPrefixes.none(importPath::startsWith)) return null
        return MiniAppSourceImportViolation(projectPath, sourcePath, importPath, AUDIO_REPLACEMENT)
    }

    private fun violation(project: String, configuration: String, dependency: String, replacement: String) =
        MiniAppDependencyViolation(project, configuration, dependency, replacement)

    private fun replacementFor(path: String): String = when {
        path.startsWith(":feature:") -> ":miniapp:compose"
        path.startsWith(":game:") || path.startsWith(":miniapp:samples:") -> ":miniapp:api"
        else -> ":miniapp:compose or a stable :core contract"
    }
}
