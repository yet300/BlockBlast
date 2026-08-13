plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.common)

            implementation(projects.game.blockblast)
            implementation(projects.feature.home)
            implementation(projects.feature.settings)

            implementation(libs.bundles.decompose)
            implementation(libs.bundles.mvi)

        }

        commonTest.dependencies {
            implementation(libs.bundles.testing)
        }
    }
}
