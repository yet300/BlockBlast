import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.local.compose.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "ge.yet.game.miniapp.integration"
        androidResources.enable = true
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    targets.named<KotlinNativeTarget>("iosSimulatorArm64") {
        binaries.framework {
            baseName = "MiniAppIntegration"
            binaryOption("bundleId", "ge.yet.game.miniapp.integration")
        }
    }

    sourceSets.commonMain.dependencies {
        api(projects.miniapp.samples.counter)
        implementation(projects.core.common)
        implementation(projects.core.domain)
        implementation(projects.core.data)
        implementation(projects.core.telemetry)
        implementation(projects.feature.review)
        implementation(projects.feature.root)
        implementation(projects.feature.catalog)
        implementation(projects.feature.settings)
        implementation(projects.miniapp.metro)
        implementation(projects.miniapp.testkit)
        implementation(libs.compose.components.resources)
    }

    sourceSets.named("androidHostTest") {
        dependencies {
            implementation(projects.composeApp)
            implementation(kotlin("test-junit"))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.core.ktx)
            implementation(libs.junit)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.robolectric)
        }
    }

    sourceSets.iosTest.dependencies {
        implementation(projects.miniapp.bundle)
    }
}
