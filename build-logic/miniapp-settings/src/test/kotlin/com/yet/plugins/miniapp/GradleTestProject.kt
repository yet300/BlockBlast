package com.yet.plugins.miniapp

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path

internal class GradleTestProject(private val root: Path) {
    fun write(relativePath: String, content: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, content.trimIndent())
    }

    fun run(vararg arguments: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(root.toFile())
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")
            .forwardOutput()
            .build()

    fun runAndFail(vararg arguments: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(root.toFile())
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")
            .forwardOutput()
            .buildAndFail()
}
