plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.local.compose.multiplatform)
    alias(libs.plugins.metro)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "ge.yet.game.blockblast.generated.resources"
}

kotlin {
    android {
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.uikit)
            implementation(projects.monetization.ads)

            implementation(libs.compose.components.resources)

            implementation(libs.bundles.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.bundles.mvi)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
        }

        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
        }
    }
}
