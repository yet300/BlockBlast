plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.common)

            implementation(libs.bundles.multiplatform.settings)

        }
        commonTest.dependencies {
            implementation(libs.bundles.testing)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.multiplatform.settings.coroutines)
        }
    }
}