plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.local.compose.multiplatform)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.miniapp.api)
        api(projects.miniapp.compose)
        api(projects.miniapp.metro)
        api(libs.decompose)
        api(libs.compose.components.resources)
        api(libs.kotlin.test)
    }
}
