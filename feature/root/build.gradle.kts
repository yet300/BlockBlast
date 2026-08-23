plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.common)

            implementation(projects.feature.catalog)
            implementation(projects.feature.settings)
            implementation(projects.feature.review)
            implementation(projects.miniapp.api)
            implementation(projects.miniapp.compose)

            implementation(libs.bundles.decompose)
        }

        commonTest.dependencies {
            implementation(libs.bundles.testing)
            implementation(projects.miniapp.testkit)
        }
    }
}
