plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.confettikit)

            implementation(libs.bundles.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.bundles.mvi)
            implementation(libs.multiplatform.settings)
        }

        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
        }
    }
}
