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
        implementation(projects.miniapp.metro)
        implementation(projects.miniapp.testkit)
        implementation(libs.compose.components.resources)
    }

    sourceSets.named("androidHostTest") {
        dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}
