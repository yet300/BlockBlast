package com.yet.plugins.miniapp

import org.junit.Test
import kotlin.test.assertEquals

class MiniAppDependencyBoundaryTest {

    @Test
    fun `all inward main contracts and self are allowed`() {
        listOf(
            ":miniapp:api", ":miniapp:compose", ":miniapp:metro", ":core:common", ":core:domain",
            ":core:uikit", ":monetization:core", ":game:snake",
        ).forEach { dependency ->
            assertEquals(null, MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainImplementation", dependency))
        }
    }

    @Test
    fun `all prohibited architecture edges receive inward replacements`() {
        assertEquals(":miniapp:compose", MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainApi", ":feature:root")?.replacement)
        assertEquals(":miniapp:api", MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainApi", ":game:other")?.replacement)
        assertEquals(":miniapp:api", MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainApi", ":miniapp:samples:counter")?.replacement)
        assertEquals(":miniapp:compose or a stable :core contract", MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainApi", ":androidApp")?.replacement)
        assertEquals(":miniapp:compose or a stable :core contract", MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainApi", ":composeApp")?.replacement)
    }
    @Test
    fun `ads maps to the compose interstitial capability`() {
        assertEquals(
            ":game:snake: commonMainImplementation may not depend on :monetization:ads; use :miniapp:compose MiniAppInterstitialCapability",
            MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainImplementation", ":monetization:ads")?.message(),
        )
    }

    @Test
    fun `testkit is restricted to test configurations`() {
        assertEquals(null, MiniAppDependencyBoundary.violationFor(":game:snake", "commonTestImplementation", ":miniapp:testkit"))
        assertEquals(":miniapp:api", MiniAppDependencyBoundary.violationFor(":game:snake", "commonMainImplementation", ":miniapp:testkit")?.replacement)
    }

    @Test
    fun `raw multiplatform settings artifacts are rejected in every configuration`() {
        assertEquals(
            ":game:snake: commonMainImplementation may not depend on com.russhwolf:multiplatform-settings; use MiniAppStorage",
            MiniAppDependencyBoundary.externalViolationFor(
                projectPath = ":game:snake",
                configuration = "commonMainImplementation",
                group = "com.russhwolf",
                name = "multiplatform-settings",
            )?.message(),
        )
        assertEquals(
            ":game:snake: commonTestImplementation may not depend on com.russhwolf:multiplatform-settings-test; use MiniAppStorage test fixtures",
            MiniAppDependencyBoundary.externalViolationFor(
                projectPath = ":game:snake",
                configuration = "commonTestImplementation",
                group = "com.russhwolf",
                name = "multiplatform-settings-test",
            )?.message(),
        )
    }
}
