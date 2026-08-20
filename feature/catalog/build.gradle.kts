plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.local.compose.multiplatform)
    alias(libs.plugins.metro)
}

compose.resources {
    publicResClass = false
    packageOfResClass = "ge.yet.game.feature.catalog.generated.resources"
}

kotlin {
    android {
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.miniapp.compose)
            implementation(projects.core.common)
            implementation(projects.core.uikit)
            implementation(libs.bundles.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.compose.components.resources)
        }
    }
}
