package com.yet.plugins.miniapp

import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains

class ValidateMiniAppDependenciesTaskTest {
    @Test
    fun `violation message is stable`() {
        val violation = MiniAppDependencyViolation(":game:snake", "commonMainApi", ":feature:root", ":miniapp:compose")
        assertFailsWith<IllegalStateException> {
            check(false) { "Mini-app dependency boundary violations:\n${violation.message()}" }
        }
    }

    @Test
    fun `duplicate violation inputs are normalized once in stable order`() {
        assertEquals(
            listOf("a", "b"),
            normalizeMiniAppDependencyViolations(listOf("b", "a", "b")),
        )
    }

    @Test
    fun `core implementation modules are rejected`() {
        assertFailsWith<IllegalStateException> {
            check(MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainImplementation", ":core:data") == null)
        }
        assertFailsWith<IllegalStateException> {
            check(MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainImplementation", ":core:telemetry") == null)
        }
    }

    @Test
    fun `policy allows only stable direct contracts and testkit in test configurations`() {
        listOf(
            ":miniapp:api", ":miniapp:compose", ":miniapp:metro", ":core:common", ":core:domain",
            ":core:uikit", ":monetization:core",
        ).forEach { dependency ->
            assertEquals(null, MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainApi", dependency))
        }
        assertEquals(null, MiniAppDependencyBoundary.violationFor(":game:snake", "commonTestImplementation", ":miniapp:testkit"))
        assertEquals(null, MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainImplementation", ":game:snake"))
    }

    @Test
    fun `actual validation rejects sorted forbidden direct dependencies and reuses cache for allowed project`() {
        val folder = TemporaryFolder().also { it.create() }
        val project = MiniAppBundleGradleTestProject(folder, useMarker = false)
        project.write("core/data/build.gradle.kts", "plugins { id(\"com.plugins.kotlinMultiplatformPlugin\") }")
        project.write("feature/root/build.gradle.kts", "plugins { id(\"com.plugins.kotlinMultiplatformPlugin\") }")
        project.write(
            "game/blockblast/build.gradle.kts",
            """
                plugins { id("logica.miniapp") }
                dependencies {
                    add("commonMainImplementation", project(":feature:root"))
                    add("commonMainApi", project(":core:data"))
                    add("commonMainApi", project(":core:telemetry"))
                    add("commonMainImplementation", project(":game:other"))
                    add("commonMainImplementation", project(":miniapp:samples:discovered"))
                    add("commonMainImplementation", project(":composeApp"))
                    add("commonMainImplementation", project(":androidApp"))
                    add("commonMainImplementation", project(":monetization:ads"))
                    add("commonMainImplementation", project(":miniapp:testkit"))
                }
            """,
        )
        val failure = project.runAndFail(":game:blockblast:validateMiniAppDependencies")
        val expected = listOf(
            ":game:blockblast: commonMainApi may not depend on :core:data; use :miniapp:api or a stable :core contract",
            ":game:blockblast: commonMainApi may not depend on :core:telemetry; use :miniapp:api or a stable :core contract",
            ":game:blockblast: commonMainImplementation may not depend on :androidApp; use :miniapp:compose or a stable :core contract",
            ":game:blockblast: commonMainImplementation may not depend on :composeApp; use :miniapp:compose or a stable :core contract",
            ":game:blockblast: commonMainImplementation may not depend on :feature:root; use :miniapp:compose",
            ":game:blockblast: commonMainImplementation may not depend on :game:other; use :miniapp:api",
            ":game:blockblast: commonMainImplementation may not depend on :miniapp:samples:discovered; use :miniapp:api",
            ":game:blockblast: commonMainImplementation may not depend on :miniapp:testkit; use :miniapp:api",
            ":game:blockblast: commonMainImplementation may not depend on :monetization:ads; use :miniapp:compose MiniAppInterstitialCapability",
        ).sorted()
        assertContains(failure.output, "Mini-app dependency boundary violations:\n")
        expected.forEach { assertContains(failure.output, it) }
        assertEquals(expected, expected.sortedBy { failure.output.indexOf(it) })
    }

    @Test
    fun `actual validation rejects direct project dependency declared in a custom declarable configuration`() {
        val folder = TemporaryFolder().also { it.create() }
        val project = MiniAppBundleGradleTestProject(folder, useMarker = false)
        project.write("miniapp/api/build.gradle.kts", "plugins { id(\"com.plugins.kotlinMultiplatformPlugin\") }")
        project.write("miniapp/compose/build.gradle.kts", "plugins { id(\"com.plugins.kotlinMultiplatformPlugin\") }")
        project.write(
            "game/blockblast/build.gradle.kts",
            """
                plugins { id("logica.miniapp") }
                val customMiniAppApi by configurations.creating {
                    isCanBeDeclared = true
                    isCanBeResolved = false
                    isCanBeConsumed = false
                }
                dependencies.add(customMiniAppApi.name, project(":feature:root"))
            """,
        )

        val failure = project.runAndFail(":game:blockblast:validateMiniAppDependencies")
        assertContains(
            failure.output,
            ":game:blockblast: customMiniAppApi may not depend on :feature:root; use :miniapp:compose",
        )
    }

    @Test
    fun `actual validation observes the final role of configurations created in normal DSL order`() {
        val folder = TemporaryFolder().also { it.create() }
        val project = MiniAppBundleGradleTestProject(folder, useMarker = false)
        project.write(
            "game/blockblast/build.gradle.kts",
            """
                plugins { id("logica.miniapp") }
                val eventuallyNonDeclarable = configurations.create("eventuallyNonDeclarable") {
                    dependencies.add(project.dependencies.project(mapOf("path" to ":feature:root")))
                    isCanBeDeclared = false
                    isCanBeResolved = false
                    isCanBeConsumed = true
                }
                val eventuallyDeclarable = configurations.create("eventuallyDeclarable") {
                    isCanBeDeclared = false
                    isCanBeResolved = false
                    isCanBeConsumed = true
                    isCanBeDeclared = true
                    isCanBeConsumed = false
                    dependencies.add(project.dependencies.project(mapOf("path" to ":feature:root")))
                }
            """,
        )

        val failure = project.runAndFail(
            ":game:blockblast:validateMiniAppDependencies",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        val repeatedFailure = project.runAndFail(
            ":game:blockblast:validateMiniAppDependencies",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        assertEquals(false, failure.output.contains("eventuallyNonDeclarable may not depend"))
        assertContains(
            failure.output,
            ":game:blockblast: eventuallyDeclarable may not depend on :feature:root; use :miniapp:compose",
        )
        assertContains(repeatedFailure.output, "Reusing configuration cache")
    }

    @Test
    fun `actual validation realizes a lazy registered declarable configuration`() {
        val folder = TemporaryFolder().also { it.create() }
        val project = MiniAppBundleGradleTestProject(folder, useMarker = false)
        project.write(
            "game/blockblast/build.gradle.kts",
            """
                plugins { id("logica.miniapp") }
                configurations.register("lazyForbidden") {
                    isCanBeDeclared = true
                    isCanBeResolved = false
                    isCanBeConsumed = false
                    dependencies.add(project.dependencies.project(mapOf("path" to ":feature:root")))
                }
            """,
        )

        val failure = project.runAndFail(
            ":game:blockblast:validateMiniAppDependencies",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        val repeatedFailure = project.runAndFail(
            ":game:blockblast:validateMiniAppDependencies",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        assertContains(
            failure.output,
            ":game:blockblast: lazyForbidden may not depend on :feature:root; use :miniapp:compose",
        )
        assertContains(repeatedFailure.output, "Reusing configuration cache")
    }

    @Test
    fun `actual validation ignores external indirect and nondeclarable dependencies without resolution`() {
        val folder = TemporaryFolder().also { it.create() }
        val project = MiniAppBundleGradleTestProject(folder, useMarker = false)
        project.write(
            "miniapp/api/build.gradle.kts",
            """
                plugins { id("com.plugins.kotlinMultiplatformPlugin") }
                dependencies { add("commonMainImplementation", project(":feature:root")) }
            """,
        )
        project.write(
            "game/blockblast/build.gradle.kts",
            """
                plugins { id("logica.miniapp") }
                val published by configurations.creating {
                    isCanBeDeclared = false
                    isCanBeResolved = false
                    isCanBeConsumed = true
                }
                dependencies {
                    add("commonMainApi", project(":miniapp:api"))
                    add("commonMainImplementation", "example:ignored:1.0")
                }
                configurations.configureEach {
                    incoming.beforeResolve { error("validateMiniAppDependencies must not resolve dependencies") }
                }
            """,
        )

        val first = project.run(
            ":game:blockblast:validateMiniAppDependencies",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        val second = project.run(
            ":game:blockblast:validateMiniAppDependencies",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        assertContains(first.output, "BUILD SUCCESSFUL")
        assertContains(second.output, "Reusing configuration cache")
    }

    @Test
    fun `actual validation rejects raw settings artifacts without resolving them`() {
        val folder = TemporaryFolder().also { it.create() }
        val project = MiniAppBundleGradleTestProject(folder, useMarker = false)
        project.write(
            "game/blockblast/build.gradle.kts",
            """
                plugins { id("logica.miniapp") }
                dependencies {
                    add("commonMainImplementation", "com.russhwolf:multiplatform-settings:1.3.0")
                    add("commonTestImplementation", "com.russhwolf:multiplatform-settings-test:1.3.0")
                }
                configurations.configureEach {
                    incoming.beforeResolve { error("validation must not resolve dependencies") }
                }
            """,
        )

        val failure = project.runAndFail(
            ":game:blockblast:validateMiniAppDependencies",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )

        assertContains(
            failure.output,
            ":game:blockblast: commonMainImplementation may not depend on com.russhwolf:multiplatform-settings; use MiniAppStorage",
        )
        assertContains(
            failure.output,
            ":game:blockblast: commonTestImplementation may not depend on com.russhwolf:multiplatform-settings-test; use MiniAppStorage test fixtures",
        )
    }
}
