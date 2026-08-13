package com.yet.plugins.miniapp

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal fun normalizeMiniAppDependencyViolations(violations: Iterable<String>): List<String> =
    violations.distinct().sorted()

@DisableCachingByDefault(because = "Verification task has no outputs")
internal abstract class ValidateMiniAppDependenciesTask : DefaultTask() {
    @get:Input abstract val violations: ListProperty<String>

    @TaskAction
    fun validate() {
        val failures = normalizeMiniAppDependencyViolations(violations.get())
        check(failures.isEmpty()) { "Mini-app dependency boundary violations:\n${failures.joinToString("\n")}" }
    }
}
