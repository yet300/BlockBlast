package com.yet.plugins.miniapp

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniAppSettingsPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `discovers mini-app projects in sorted production order`() {
        val root = temporaryFolder.newFolder().toPath()
        listOf(
            "game/zeta/build.gradle.kts",
            "game/alpha/build.gradle.kts",
            "miniapp/samples/counter/build.gradle.kts",
            "game/nested/child/build.gradle.kts",
            "miniapp/samples/nested/child/build.gradle.kts",
        ).forEach { relativePath ->
            val buildFile = root.resolve(relativePath)
            Files.createDirectories(buildFile.parent)
            Files.writeString(buildFile, "")
        }

        assertEquals(
            listOf(":game:alpha", ":game:zeta", ":miniapp:samples:counter"),
            discoverMiniAppProjectPaths(root.toFile()),
        )
    }

    @Test
    fun `discovers only direct game and sample projects in sorted order`() {
        val project = testProject(
            settings = settingsScript(),
            rootBuild = printProjectsBuildScript(),
        )
        project.write("game/zeta/build.gradle.kts", "")
        project.write("game/alpha/build.gradle.kts", "")
        project.write("miniapp/samples/counter/build.gradle.kts", "")
        project.write("game/draft/readme.txt", "")
        project.write("game/nested/child/build.gradle.kts", "")
        project.write("miniapp/samples/nested/child/build.gradle.kts", "")
        project.write("sandbox/hidden/build.gradle.kts", "")

        val result = project.run("printProjects")

        assertContains(result.output, "PROJECTS=:game:alpha,:game:zeta,:miniapp:samples:counter")
        assertFalse(result.output.contains(":game:draft"))
        assertFalse(result.output.contains(":game:nested:child"))
        assertFalse(result.output.contains(":miniapp:samples:nested:child"))
        assertFalse(result.output.contains(":sandbox:hidden"))
    }

    @Test
    fun `publishes a typed shipping model to other settings plugins`() {
        val project = testProject(
            settings = settingsScript(
                preamble = "import com.yet.plugins.miniapp.MiniAppShippingModel",
                miniApps = """
                    miniApps {
                        include(":game:alpha", "game.alpha")
                    }

                    gradle.settingsEvaluated {
                        val model = gradle.extensions.getByType(MiniAppShippingModel::class.java)
                        check(model.declarations.get().single().projectPath == ":game:alpha")
                        check(model.declarations.get().single().expectedId == "game.alpha")
                    }
                """,
            ),
            rootBuild = "",
        )
        project.write("game/alpha/build.gradle.kts", "")

        project.run("help")
    }

    @Test
    fun `rejects declarations added after the shipping model is finalized`() {
        val project = testProject(
            settings = settingsScript(
                miniApps = """
                    miniApps {
                        val configuredMiniApps = this
                        gradle.settingsEvaluated {
                            configuredMiniApps.include(":game:alpha", "game.alpha")
                        }
                    }
                """,
            ),
            rootBuild = "",
        )
        project.write("game/alpha/build.gradle.kts", "")

        val result = project.runAndFail("help")

        assertContains(result.output, "Mini-app declarations are already finalized")
    }

    @Test
    fun `allowlist rejects malformed id duplicate path duplicate id and undiscovered path`() {
        assertSettingsFailure(
            miniApps = """
                miniApps {
                    include(":game:alpha", "Game.Alpha")
                }
            """,
            expectedMessage = "Invalid mini-app id",
        )
        assertSettingsFailure(
            miniApps = """
                miniApps {
                    include(":game:alpha", "game.alpha")
                    include(":game:alpha", "game.alpha2")
                }
            """,
            expectedMessage = "Duplicate mini-app project path",
        )
        assertSettingsFailure(
            miniApps = """
                miniApps {
                    include(":game:alpha", "game.alpha")
                    include(":game:zeta", "game.alpha")
                }
            """,
            expectedMessage = "Duplicate mini-app id",
        )
        assertSettingsFailure(
            miniApps = """
                miniApps {
                    include(":game:missing", "game.missing")
                }
            """,
            expectedMessage = "is not a discovered mini-app project",
        )
    }

    @Test
    fun `discovery alone does not add a bundle dependency`() {
        val project = testProject(
            settings = settingsScript(),
            rootBuild = """
                import org.gradle.api.artifacts.ProjectDependency

                tasks.register("printBundleProjects") {
                    doLast {
                        val projectDependencies = rootProject.allprojects.flatMap { project ->
                            project.configurations.flatMap { configuration ->
                                configuration.dependencies
                                    .filterIsInstance<ProjectDependency>()
                                    .map { dependency -> "${'$'}{project.path}->${'$'}{dependency.name}" }
                            }
                        }
                        println("BUNDLE_PROJECTS=" + projectDependencies.joinToString(","))
                    }
                }
            """,
        )
        project.write("game/blockblast/build.gradle.kts", "")

        val result = project.run("printBundleProjects")

        assertTrue(result.output.lineSequence().any { it.trim() == "BUNDLE_PROJECTS=" })
        assertFalse(result.output.contains(":game:blockblast"))
    }

    @Test
    fun `settings model reuses configuration cache`() {
        val project = testProject(
            settings = settingsScript(),
            rootBuild = "",
        )

        project.run("help", "--configuration-cache")
        val secondRun = project.run("help", "--configuration-cache")

        assertContains(secondRun.output, "Reusing configuration cache")
    }

    private fun assertSettingsFailure(miniApps: String, expectedMessage: String) {
        val project = testProject(
            settings = settingsScript(miniApps),
            rootBuild = "",
        )
        project.write("game/alpha/build.gradle.kts", "")
        project.write("game/zeta/build.gradle.kts", "")

        val result = project.runAndFail("help")

        assertContains(result.output, expectedMessage)
    }

    private fun testProject(settings: String, rootBuild: String): GradleTestProject =
        GradleTestProject(temporaryFolder.newFolder().toPath()).also { project ->
            project.write("settings.gradle.kts", settings)
            project.write("build.gradle.kts", rootBuild)
        }

    private fun settingsScript(
        miniApps: String = "miniApps { }",
        preamble: String = "",
    ): String =
        """
            $preamble

            pluginManagement {
                repositories { google(); mavenCentral(); gradlePluginPortal() }
                plugins {
                    id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
                    id("com.android.kotlin.multiplatform.library") version "9.2.1"
                    id("org.jetbrains.compose") version "1.11.1"
                    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
                    id("dev.zacsweers.metro") version "1.4.1"
                }
            }

            plugins {
                id("logica.miniapp.settings")
            }

            $miniApps
        """

    private fun printProjectsBuildScript(): String =
        """
            tasks.register("printProjects") {
                doLast {
                    val projectPaths = rootProject.allprojects
                        .map { it.path }
                        .filter { it.startsWith(":game:") || it.startsWith(":miniapp:samples:") }
                    println("PROJECTS=" + projectPaths.joinToString(","))
                }
            }
        """
}
