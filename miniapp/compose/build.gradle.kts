plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.local.compose.multiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.miniapp.api)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.components.resources)
            api(libs.decompose)
        }
    }
}
