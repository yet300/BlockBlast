package com.yet.plugins.miniapp

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains

class MiniAppConventionPluginTest {
    @Test
    fun `resource package is deterministic and unique for project path`() {
        assertEquals(
            "ge.yet.game.blockblast.generated.resources",
            MiniAppResourcePackage.from(":game:blockblast"),
        )
        assertEquals(
            "ge.yet.miniapp.samples.counter.generated.resources",
            MiniAppResourcePackage.from(":miniapp:samples:counter"),
        )
    }

    @Test
    fun `invalid resource project paths are rejected`() {
        assertFailsWith<IllegalArgumentException> { MiniAppResourcePackage.from(":game:BlockBlast") }
    }

    @Test
    fun `actual convention configures plugins resources dependencies and validation wiring`() {
        val project = MiniAppBundleGradleTestProject(org.junit.rules.TemporaryFolder().also { it.create() })
        project.write(
            "game/blockblast/build.gradle.kts",
            """
                plugins { id("logica.miniapp") }
                val kotlin = extensions.getByName("kotlin") as org.gradle.api.plugins.ExtensionAware
                val android = kotlin.extensions.getByName("android")
                val resources = android.javaClass.getMethod("getAndroidResources").invoke(android)
                val compose = extensions.getByName("compose") as org.gradle.api.plugins.ExtensionAware
                val composeResources = compose.extensions.getByName("resources")
                val dependencies = configurations.getByName("commonMainApi").dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>().map { it.path }.sorted()
                val implementationProjects = configurations.getByName("commonMainImplementation").dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>().map { it.path }.sorted()
                val implementationModules = configurations.getByName("commonMainImplementation").dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
                    .map { "${'$'}{it.group}:${'$'}{it.name}" }.sorted()
                val probe = listOf(
                            plugins.hasPlugin("com.plugins.kotlinMultiplatformPlugin"),
                            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform"),
                            plugins.hasPlugin("com.android.kotlin.multiplatform.library"),
                            plugins.hasPlugin("org.jetbrains.kotlin.plugin.serialization"),
                            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform"),
                            plugins.hasPlugin("org.jetbrains.compose"),
                            plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose"),
                            plugins.hasPlugin("dev.zacsweers.metro"),
                            resources.javaClass.getMethod("getEnable").invoke(resources),
                            composeResources.javaClass.getMethod("getPublicResClass").invoke(composeResources),
                            composeResources.javaClass.getMethod("getPackageOfResClass").invoke(composeResources),
                            dependencies,
                            implementationProjects,
                            implementationModules.contains("org.jetbrains.compose.components:components-resources"),
                            implementationModules.contains("com.arkivanov.decompose:extensions-compose"),
                            configurations.getByName("commonTestImplementation").dependencies.filterIsInstance<org.gradle.api.artifacts.ProjectDependency>().map { it.path }.sorted(),
                            tasks.getByName("check").taskDependencies.getDependencies(tasks.getByName("check")).map { it.name }.contains("validateMiniAppDependencies"),
                            tasks.getByName("allTests").taskDependencies.getDependencies(tasks.getByName("allTests")).map { it.name }.contains("validateMiniAppDependencies"),
                )
                tasks.register("probeConvention") { doLast { println("PROBE=" + probe) } }
            """,
        )
        val first = project.run(":game:blockblast:probeConvention")
        project.run(":game:blockblast:validateMiniAppDependencies", "--configuration-cache", "--configuration-cache-problems=fail")
        val second = project.run(":game:blockblast:validateMiniAppDependencies", "--configuration-cache", "--configuration-cache-problems=fail")
        assertContains(first.output, "PROBE=[true, true, true, true, true, true, true, true, true, false, ge.yet.game.blockblast.generated.resources, [:miniapp:metro], [:miniapp:audio-presets], true, true, [:miniapp:testkit], true, true]")
        assertContains(second.output, "Reusing configuration cache")
    }
}
