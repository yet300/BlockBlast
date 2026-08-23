plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.common)
            implementation(projects.miniapp.api)

            implementation(libs.bundles.decompose)
            implementation(libs.bundles.mvi)
        }
    }
}
