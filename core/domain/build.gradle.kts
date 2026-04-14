plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {

    sourceSets {
        commonMain.dependencies {}
    }
}