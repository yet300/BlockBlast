package com.yet.plugins.miniapp

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

private val miniAppProjectPath = Regex("^:(game|miniapp:samples):([a-z][a-z0-9]*)$")

internal fun validatedProjectDirectory(root: File, projectPath: String): File {
    require(miniAppProjectPath.matches(projectPath)) { "Mini-app project path must be :game:<name> or :miniapp:samples:<name>" }
    val rootPath = root.toPath().toRealPath()
    val relativePath = Path.of(projectPath.removePrefix(":").replace(':', File.separatorChar))
    val target = rootPath.resolve(relativePath).normalize()
    require(target.startsWith(rootPath)) { "Mini-app path escapes repository root" }
    var segment = rootPath
    relativePath.forEach { part ->
        segment = segment.resolve(part)
        if (Files.exists(segment, NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(segment)) { "Mini-app path contains a symbolic link: $segment" }
        }
    }
    return target.toFile()
}

internal fun createMiniAppWithoutReplacing(
    target: File,
    validateTarget: () -> Unit = {},
    renderer: (File) -> Unit,
) {
    val targetPath = target.toPath()
    check(!Files.exists(targetPath, NOFOLLOW_LINKS)) { "Refusing to overwrite existing mini-app project ${target.invariantSeparatorsPath}" }
    validateTarget()
    check(!Files.exists(targetPath, NOFOLLOW_LINKS)) { "Refusing to overwrite existing mini-app project ${target.invariantSeparatorsPath}" }
    val parent = requireNotNull(target.toPath().parent)
    Files.createDirectories(parent)
    validateTarget()
    check(!Files.exists(targetPath, NOFOLLOW_LINKS)) { "Refusing to overwrite existing mini-app project ${target.invariantSeparatorsPath}" }
    val staging = Files.createTempDirectory(parent, ".${target.name}.staging-")
    try {
        renderer(staging.toFile())
        validateTarget()
        check(!Files.exists(targetPath, NOFOLLOW_LINKS)) { "Refusing to overwrite existing mini-app project ${target.invariantSeparatorsPath}" }
        Files.move(staging, targetPath)
    } catch (failure: Throwable) {
        staging.toFile().deleteRecursively()
        throw failure
    }
}

@UntrackedTask(because = "Creates reviewable source in a newly discovered project")
internal abstract class CreateMiniAppTask : DefaultTask() {
    @get:org.gradle.api.tasks.Internal abstract val repositoryRoot: DirectoryProperty
    @get:Input abstract val miniAppId: Property<String>
    @get:Input abstract val miniAppName: Property<String>
    @get:Optional @get:Input abstract val miniAppProjectPath: Property<String>
    @get:Optional @get:Input abstract val miniAppProfile: Property<String>

    @TaskAction
    fun create() {
        val id = miniAppId.get()
        MiniAppIdSyntax.requireValid(id)
        val name = miniAppName.get().trim()
        require(name.isNotEmpty()) { "miniAppName must not be blank" }
        val path = miniAppProjectPath.orNull ?: ":game:${id.substringAfterLast('.')}"
        val profile = MiniAppScaffoldProfile.from(miniAppProfile.orNull)
        val root = repositoryRoot.get().asFile
        val target = validatedProjectDirectory(root, path)
        check(!Files.exists(target.toPath(), NOFOLLOW_LINKS)) { "Refusing to overwrite existing mini-app project $path" }
        createMiniAppWithoutReplacing(
            target = target,
            validateTarget = { check(validatedProjectDirectory(root, path) == target) { "Mini-app target changed during creation" } },
        ) { staging -> MiniAppScaffoldRenderer(id, name, path, profile).writeTo(staging) }
        logger.lifecycle("Created $path; it becomes discoverable on the next Gradle invocation")
    }
}
