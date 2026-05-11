import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.metro)

    alias(libs.plugins.crashlytics)
    alias(libs.plugins.google.services)
}

android {
    namespace = "ge.yet.blokblast"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ge.yet.blokblast"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 7
        versionName = "1.3.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.composeApp)

    implementation(libs.bundles.decompose)

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    implementation(libs.play.services.ads)

    implementation(project.dependencies.platform(libs.firebase.android.bom))
    implementation(libs.gitlive.firebase.kotlin.crashlytics)
    implementation(libs.firebase.android.crashlytics.ktx)

}