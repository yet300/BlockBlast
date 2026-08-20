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
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
        }

        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
        }
    }
}
