plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {
    android {
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.pattern)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.miniapp.api)
            api(libs.essenty.lifecycle)
        }
        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
    }
}
