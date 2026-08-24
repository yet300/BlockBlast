plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.miniapp.api)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(libs.bundles.multiplatform.settings)
        }

        commonTest.dependencies {
            implementation(libs.bundles.testing)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
