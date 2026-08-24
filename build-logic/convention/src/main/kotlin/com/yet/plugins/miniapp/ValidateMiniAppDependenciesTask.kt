package com.yet.plugins.miniapp

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal fun normalizeMiniAppDependencyViolations(violations: Iterable<String>): List<String> =
    violations.distinct().sorted()

@DisableCachingByDefault(because = "Verification task has no outputs")
internal abstract class ValidateMiniAppDependenciesTask : DefaultTask() {
    @get:Input abstract val violations: ListProperty<String>
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:Input abstract val sourceRootPath: Property<String>
    @get:Input abstract val miniAppProjectPath: Property<String>

    @TaskAction
    fun validate() {
        val root = java.io.File(sourceRootPath.get())
        val sourceViolations = sourceFiles.files
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .flatMap { source ->
                val relativePath = source.relativeTo(root).invariantSeparatorsPath
                source.useLines { lines ->
                    lines.mapNotNull(::kotlinImportPath)
                        .mapNotNull { importPath ->
                            MiniAppDependencyBoundary.sourceImportViolationFor(
                                projectPath = miniAppProjectPath.get(),
                                sourcePath = relativePath,
                                importPath = importPath,
                            )?.message()
                        }
                        .toList()
                }
            }
        val failures = normalizeMiniAppDependencyViolations(violations.get() + sourceViolations)
        check(failures.isEmpty()) { "Mini-app dependency boundary violations:\n${failures.joinToString("\n")}" }
    }
}

private fun kotlinImportPath(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("import ")) return null
    return trimmed.removePrefix("import ").substringBefore(" as ").trim().takeIf(String::isNotEmpty)
}
