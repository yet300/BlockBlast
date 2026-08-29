plugins { alias(libs.plugins.miniapp) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.uikit)
            implementation(libs.bundles.mvi)
        }
    }
}
