plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.metro)
}

kotlin {

    sourceSets {
        androidMain.dependencies {
            // Pins the unversioned Firebase Android artifacts pulled in
            // transitively by dev.gitlive:firebase-crashlytics.
            implementation(project.dependencies.platform(libs.firebase.android.bom))
        }
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.common)

            implementation(libs.bundles.multiplatform.settings)

            implementation(libs.gitlive.firebase.kotlin.crashlytics)
            implementation(libs.inappreview.kmp.google.play)

        }
        commonTest.dependencies {
            implementation(libs.bundles.testing)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.multiplatform.settings.coroutines)
        }
    }
}