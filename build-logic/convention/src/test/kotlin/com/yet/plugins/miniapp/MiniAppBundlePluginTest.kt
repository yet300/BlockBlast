package com.yet.plugins.miniapp

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MiniAppBundlePluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `allowlisted project is exactly one commonMain api dependency`() {
        val project = testProject()
        project.write(
            "miniapp/bundle/build.gradle.kts",
            """
                plugins { id("logica.miniapp.bundle") }

                tasks.register("printDirectCommonMainApiDependencies") {
                    doLast {
                        val paths = configurations.getByName("commonMainApi").dependencies
                            .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                            .map { it.path }
                        println("COMMON_MAIN_API=" + paths.joinToString(","))
                    }
                }
            """,
        )

        val result = project.run(":miniapp:bundle:printDirectCommonMainApiDependencies")

        assertContains(result.output, "COMMON_MAIN_API=:miniapp:metro,:game:blockblast")
    }

    @Test
    fun `discovered non allowlisted project is absent from bundle`() {
        val project = testProject()
        project.write(
            "miniapp/bundle/build.gradle.kts",
            """
                plugins { id("logica.miniapp.bundle") }

                tasks.register("printDirectCommonMainApiDependencies") {
                    doLast {
                        val paths = configurations.getByName("commonMainApi").dependencies
                            .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                            .map { it.path }
                        println("COMMON_MAIN_API=" + paths.joinToString(","))
                    }
                }
            """,
        )

        val result = project.run(":miniapp:bundle:printDirectCommonMainApiDependencies")

        assertFalse(result.output.contains(":miniapp:samples:discovered"))
    }

    @Test
    fun `allowlisted project without logica miniapp convention fails verification`() {
        val project = testProject(gameUsesMiniAppConvention = false)

        val result = project.runAndFail(":miniapp:bundle:verifyMiniAppBundle")

        assertContains(result.output, "withoutLogicaMiniApp=[:game:blockblast]")
    }

    @Test
    fun `generated expectation contains allowlist ids in declaration order`() {
        val project = testProject(
            declarations = """
                include(":game:blockblast", "game.blockblast")
                include(":game:second", "game.second")
            """,
        )
        project.write("game/second/build.gradle.kts", kotlinProjectBuildScript())

        project.run(":miniapp:bundle:generateProductionMiniApps")

        assertEquals(
            """
                package ge.yet.game.miniapp.bundle
                import dev.zacsweers.metro.AppScope
                import dev.zacsweers.metro.ContributesIntoSet
                import dev.zacsweers.metro.Inject
                import ge.yet.game.miniapp.api.MiniAppId
                import ge.yet.game.miniapp.metro.MiniAppRegistryExpectation
                @Inject
                @ContributesIntoSet(AppScope::class)
                public class ProductionMiniAppExpectation : MiniAppRegistryExpectation {
                 override val expectedIds: Set<MiniAppId> = linkedSetOf(
                        MiniAppId("game.blockblast"),
                        MiniAppId("game.second"),
                 )
                }
            """.trimIndent() + "\n",
            project.generatedExpectation(),
        )
    }

    @Test
    fun `generation removes stale files from its owned output directory`() {
        val project = testProject(
            additionalBundleDependencies =
                """
                    tasks.named("generateProductionMiniApps") {
                        doFirst {
                            file(
                                "build/generated/miniapps/commonMain/kotlin/" +
                                    "ge/yet/game/miniapp/bundle/ObsoleteProductionMiniAppExpectation.kt",
                            ).apply {
                                parentFile.mkdirs()
                                writeText("package ge.yet.game.miniapp.bundle")
                            }
                        }
                    }
                """,
        )
        val staleFile =
            "miniapp/bundle/build/generated/miniapps/commonMain/kotlin/" +
                "ge/yet/game/miniapp/bundle/ObsoleteProductionMiniAppExpectation.kt"

        project.run(":miniapp:bundle:generateProductionMiniApps", "--rerun-tasks")

        assertFalse(project.exists(staleFile))
        assertEquals(
            listOf("ge/yet/game/miniapp/bundle/ProductionMiniAppExpectation.kt"),
            project.generatedKotlinFiles(),
        )
    }

    @Test
    fun `verify task rejects a dependency not represented by allowlist`() {
        val project = testProject(
            additionalBundleDependencies =
                """
                    dependencies {
                        add("commonMainApi", project(":game:rogue"))
                    }
                """,
        )
        project.write("game/rogue/build.gradle.kts", kotlinProjectBuildScript())

        val result = project.runAndFail(":miniapp:bundle:verifyMiniAppBundle")

        assertContains(result.output, "unexpected=[:game:rogue]")
    }

    @Test
    fun `verify task rejects allowlisted dependencies in a different order`() {
        val project = testProject(
            declarations =
                """
                    include(":game:blockblast", "game.blockblast")
                    include(":game:second", "game.second")
                """,
            additionalBundleDependencies =
                """
                    val commonMainApi = configurations.getByName("commonMainApi")
                    commonMainApi.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .filter { it.path == ":game:blockblast" || it.path == ":game:second" }
                        .toList()
                        .forEach(commonMainApi.dependencies::remove)
                    dependencies {
                        add("commonMainApi", project(":game:second"))
                        add("commonMainApi", project(":game:blockblast"))
                    }
                """,
        )
        project.write("game/second/build.gradle.kts", kotlinProjectBuildScript())

        val result = project.runAndFail(":miniapp:bundle:verifyMiniAppBundle")

        assertContains(result.output, "outOfOrder=true")
    }

    @Test
    fun `bundle configuration reuses configuration cache`() {
        val project = testProject()

        project.run(
            ":miniapp:bundle:verifyMiniAppBundle",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        )
        val secondRun = project.run(
            ":miniapp:bundle:verifyMiniAppBundle",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        )

        assertContains(secondRun.output, "Reusing configuration cache")
    }

    private fun testProject(
        declarations: String = "include(\":game:blockblast\", \"game.blockblast\")",
        gameUsesMiniAppConvention: Boolean = true,
        additionalBundleDependencies: String = "",
    ): MiniAppBundleGradleTestProject =
        MiniAppBundleGradleTestProject(
            temporaryFolder = temporaryFolder,
            declarations = declarations,
            gameUsesMiniAppConvention = gameUsesMiniAppConvention,
            additionalBundleDependencies = additionalBundleDependencies,
        )

    private fun kotlinProjectBuildScript(): String =
        """
            plugins {
                id("com.plugins.kotlinMultiplatformPlugin")
                id("logica.miniapp")
            }
        """
}
