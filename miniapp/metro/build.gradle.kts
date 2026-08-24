plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.local.compose.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.miniapp.compose)
    }
}
