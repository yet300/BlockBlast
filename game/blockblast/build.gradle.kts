plugins {
    alias(libs.plugins.miniapp)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.uikit)
            implementation(libs.confettikit)
            implementation(libs.bundles.mvi)
        }

        commonTest.dependencies {
            implementation(libs.compose.ui.test)
        }
    }
}
