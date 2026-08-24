plugins { alias(libs.plugins.miniapp) }

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(projects.core.pattern)
        }
    }
}
