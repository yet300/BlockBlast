package com.yet.plugins.miniapp

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Properties

internal class MiniAppBundleGradleTestProject(
    temporaryFolder: TemporaryFolder,
    declarations: String = "include(\":game:blockblast\", \"game.blockblast\")",
    gameUsesMiniAppConvention: Boolean = true,
    additionalBundleDependencies: String = "",
    private val useMarker: Boolean = true,
) {
    private val root: Path = temporaryFolder.newFolder().toPath()

    init {
        write("build.gradle.kts", "")
        write("gradle/libs.versions.toml", versionCatalog())
        if (useMarker) {
            write("miniapp/marker/settings.gradle.kts", "rootProject.name = \"miniapp-marker\"")
            write("miniapp/marker/build.gradle.kts", markerBuildScript())
            write(
            "miniapp/marker/src/main/java/testmarker/MiniAppMarkerPlugin.java",
            """
                package testmarker;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public final class MiniAppMarkerPlugin implements Plugin<Project> {
                    @Override public void apply(Project project) {}
                }
            """,
            )
        }
        write("miniapp/api/build.gradle.kts", kotlinProjectBuildScript())
        write("miniapp/compose/build.gradle.kts", kotlinProjectBuildScript())
        write("miniapp/metro/build.gradle.kts", kotlinProjectBuildScript())
        write("miniapp/audio/build.gradle.kts", kotlinProjectBuildScript())
        write("miniapp/audio-presets/build.gradle.kts", kotlinProjectBuildScript())
        write(
            "miniapp/bundle/build.gradle.kts",
            """
                plugins { id("logica.miniapp.bundle") }

                $additionalBundleDependencies
            """,
        )
        write("miniapp/testkit/build.gradle.kts", kotlinProjectBuildScript())
        write(
            "game/blockblast/build.gradle.kts",
            kotlinProjectBuildScript(gameUsesMiniAppConvention),
        )
        write("miniapp/samples/discovered/build.gradle.kts", "")
        write("core/data/build.gradle.kts", kotlinProjectBuildScript())
        write("core/common/build.gradle.kts", kotlinProjectBuildScript())
        write("core/domain/build.gradle.kts", kotlinProjectBuildScript())
        write("core/pattern/build.gradle.kts", kotlinProjectBuildScript())
        write("core/uikit/build.gradle.kts", kotlinProjectBuildScript())
        write("core/telemetry/build.gradle.kts", kotlinProjectBuildScript())
        write("feature/root/build.gradle.kts", kotlinProjectBuildScript())
        write("game/other/build.gradle.kts", kotlinProjectBuildScript())
        write("monetization/core/build.gradle.kts", kotlinProjectBuildScript())
        write("monetization/ads/build.gradle.kts", kotlinProjectBuildScript())
        write("composeApp/build.gradle.kts", kotlinProjectBuildScript())
        write("androidApp/build.gradle.kts", kotlinProjectBuildScript())
        write("settings.gradle.kts", settingsScript(declarations, useMarker))
    }

    fun write(relativePath: String, content: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, content.trimIndent())
    }

    fun generatedExpectation(): String = Files.readString(
        root.resolve(
            "miniapp/bundle/build/generated/miniapps/commonMain/kotlin/" +
                "ge/yet/game/miniapp/bundle/ProductionMiniAppExpectation.kt",
        ),
    )

    fun exists(relativePath: String): Boolean = Files.exists(root.resolve(relativePath))

    fun read(relativePath: String): String = Files.readString(root.resolve(relativePath))

    fun hasStagingDirectory(parent: String, name: String): Boolean = Files.list(root.resolve(parent)).use { children ->
        children.anyMatch { it.fileName.toString().startsWith(".$name.staging-") }
    }

    fun copyRealMiniAppContracts() {
        val sourceRoot = Path.of(requireNotNull(System.getProperty("sourceRepositoryRoot")))
        listOf(
            "core/pattern", "miniapp/api", "miniapp/compose", "miniapp/metro", "miniapp/testkit",
            "miniapp/audio-presets",
        ).forEach { module -> copyCommonMain(sourceRoot, module) }
        copyCommonMain(sourceRoot, "miniapp/audio") { relativePath ->
            "/internal/" !in "/$relativePath" &&
                "/di/" !in "/$relativePath" &&
                "/testing/" !in "/$relativePath" &&
                !relativePath.endsWith("MiniAppAudioEngine.kt")
        }
        write("core/pattern/build.gradle.kts", """
            plugins { id("com.plugins.kotlinMultiplatformPlugin") }
        """)
        write("miniapp/api/build.gradle.kts", """
            plugins { id("com.plugins.kotlinMultiplatformPlugin") }
            kotlin { sourceSets.commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) } }
        """)
        write("miniapp/compose/build.gradle.kts", """
            plugins { id("com.plugins.kotlinMultiplatformPlugin"); id("com.plugins.composeMultiplatform") }
            kotlin { sourceSets.commonMain.dependencies { api(project(":miniapp:api")); api(project(":miniapp:audio")); api(libs.decompose); api(libs.compose.components.resources) } }
        """)
        write("miniapp/audio/build.gradle.kts", """
            plugins { id("com.plugins.kotlinMultiplatformPlugin") }
            kotlin { sourceSets.commonMain.dependencies { api(project(":core:pattern")); implementation(project(":miniapp:api")) } }
        """)
        write("miniapp/audio-presets/build.gradle.kts", """
            plugins { id("com.plugins.kotlinMultiplatformPlugin") }
            kotlin { sourceSets.commonMain.dependencies { api(project(":miniapp:audio")) } }
        """)
        write("miniapp/metro/build.gradle.kts", """
            plugins { id("com.plugins.kotlinMultiplatformPlugin"); id("com.plugins.composeMultiplatform"); id("dev.zacsweers.metro") }
            kotlin { sourceSets.commonMain.dependencies { api(project(":miniapp:compose")) } }
        """)
        write("miniapp/testkit/build.gradle.kts", """
            plugins { id("com.plugins.kotlinMultiplatformPlugin"); id("com.plugins.composeMultiplatform") }
            kotlin {
                sourceSets.commonMain.dependencies {
                    api(project(":miniapp:api"))
                    api(project(":miniapp:audio"))
                    api(project(":miniapp:compose"))
                    api(project(":miniapp:metro"))
                    api(libs.decompose)
                    api(libs.compose.components.resources)
                    api(libs.kotlin.test)
                }
            }
        """)
    }

    private fun copyCommonMain(
        sourceRoot: Path,
        module: String,
        includeFile: (String) -> Boolean = { true },
    ) {
        val source = sourceRoot.resolve("$module/src/commonMain")
        val target = root.resolve("$module/src/commonMain")
        Files.walk(source).use { paths ->
            paths.forEach { sourcePath ->
                val relativePath = source.relativize(sourcePath).toString().replace(java.io.File.separatorChar, '/')
                val destination = target.resolve(relativePath)
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(destination)
                } else if (includeFile(relativePath)) {
                    Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun generatedKotlinFiles(): List<String> {
        val outputDirectory = root.resolve("miniapp/bundle/build/generated/miniapps/commonMain/kotlin")
        return Files.walk(outputDirectory).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .map { outputDirectory.relativize(it).toString().replace(java.io.File.separatorChar, '/') }
                .sorted()
                .toList()
        }
    }

    fun run(vararg arguments: String): BuildResult = runner(arguments).build()

    fun runAndFail(vararg arguments: String): BuildResult = runner(arguments).buildAndFail()

    private fun runner(arguments: Array<out String>): GradleRunner =
        GradleRunner.create()
            .withProjectDir(root.toFile())
            .withPluginClasspath(pluginClasspath())
            .withArguments(*arguments, "--stacktrace")
            .forwardOutput()

    private fun pluginClasspath(): List<java.io.File> {
        val metadata = Properties().apply {
            Files.newBufferedReader(
                Path.of(requireNotNull(System.getProperty("conventionPluginClasspathFile"))),
                UTF_8,
            ).use(::load)
        }
        return metadata.getProperty("implementation-classpath")
            .split(java.io.File.pathSeparator)
            .map { java.io.File(it) } +
            System.getProperty("java.class.path")
                .split(java.io.File.pathSeparator)
                .map { java.io.File(it) } +
            Path.of(requireNotNull(System.getProperty("miniAppSettingsJar"))).toFile()
    }

    private fun settingsScript(declarations: String, useMarker: Boolean): String =
        """
            pluginManagement {
                ${if (useMarker) "includeBuild(\"miniapp/marker\")" else ""}
                repositories { google(); mavenCentral(); gradlePluginPortal() }
                plugins {
                    id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
                    id("com.android.kotlin.multiplatform.library") version "9.2.1"
                    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
                    id("org.jetbrains.compose") version "1.12.0"
                    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
                    id("dev.zacsweers.metro") version "1.4.1"
                }
            }

            plugins { id("logica.miniapp.settings") }

            dependencyResolutionManagement {
                repositories { google(); mavenCentral() }
            }

            include(":miniapp:api")
            include(":miniapp:compose")
            include(":miniapp:metro")
            include(":miniapp:audio")
            include(":miniapp:audio-presets")
            include(":miniapp:testkit")
            include(":miniapp:bundle")
            include(":core:common")
            include(":core:data")
            include(":core:domain")
            include(":core:pattern")
            include(":core:uikit")
            include(":core:telemetry")
            include(":feature:root")
            include(":game:other")
            include(":monetization:core")
            include(":monetization:ads")
            include(":composeApp")
            include(":androidApp")

            miniApps {
                $declarations
            }
        """

    private fun kotlinProjectBuildScript(usesMiniAppConvention: Boolean = false): String =
        """
            plugins {
                id("com.plugins.kotlinMultiplatformPlugin")
                id("org.jetbrains.compose")
                id("org.jetbrains.kotlin.plugin.compose")
                ${if (usesMiniAppConvention) "id(\"logica.miniapp\")" else ""}
            }
        """

    private fun markerBuildScript(): String =
        """
            plugins { `java-gradle-plugin` }

            gradlePlugin {
                plugins {
                    register("miniApp") {
                        id = "logica.miniapp"
                        implementationClass = "testmarker.MiniAppMarkerPlugin"
                    }
                }
            }
        """

    private fun versionCatalog(): String =
        """
            [versions]
            agp = "9.2.1"
            android-compileSdk = "37"
            android-minSdk = "24"
            kotlin = "2.4.10"
            coroutines = "1.11.0"
            datetime = "0.8.0"
            serialization = "1.11.0"
            compose = "1.12.0"
            material3 = "1.10.0-alpha05"
            material3Adaptive = "1.3.0-beta02"
            decompose = "3.5.0"

            [libraries]
            android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
            kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
            kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
            kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
            kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
            kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
            compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose" }
            compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose" }
            compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
            compose-material3-adaptive = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version.ref = "material3Adaptive" }
            compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose" }
            compose-ui-test = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "compose" }
            compose-uiTooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "compose" }
            compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "compose" }
            compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "compose" }
            decompose-compose = { module = "com.arkivanov.decompose:extensions-compose", version.ref = "decompose" }
            decompose = { module = "com.arkivanov.decompose:decompose", version.ref = "decompose" }

            [plugins]
            kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
            android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
            kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
            composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose" }
            composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }

            [bundles]
            testing = ["kotlin-test"]
        """
}
