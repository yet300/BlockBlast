plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.pattern)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.miniapp.api)
        }
    }
}
