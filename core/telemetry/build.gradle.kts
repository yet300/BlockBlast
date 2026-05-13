plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)

            implementation(libs.gitlive.firebase.kotlin.crashlytics)
            implementation(libs.gitlive.firebase.kotlin.analytics)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.android.bom))
        }
    }
}
