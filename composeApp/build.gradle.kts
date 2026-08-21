import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.aboutLibraries)

    alias(libs.plugins.metro)
}

kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_4)
        apiVersion.set(KotlinVersion.KOTLIN_2_4)
    }

    android {
        namespace = "ge.yet.game.composeApp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(libs.bundles.decompose)
            export(projects.feature.root)
            export(projects.core.common)
        }
    }
    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.telemetry)
            implementation(projects.core.uikit)
            implementation(projects.monetization.ads)
            implementation(projects.miniapp.compose)
            implementation(projects.miniapp.bundle)

            api(projects.feature.root)
            implementation(projects.feature.catalog)
            implementation(projects.feature.settings)
            implementation(projects.feature.review)


            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.aboutlibraries.core)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)

            implementation(libs.bundles.decompose)
            implementation(libs.decompose.compose)

        }
        commonTest.dependencies {
            implementation(libs.bundles.testing)
            implementation(libs.compose.ui.test)

            implementation(libs.multiplatform.settings.test)
        }
        named("androidHostTest") {
            dependencies {
                implementation(projects.miniapp.testkit)
                implementation(kotlin("test-junit"))
                implementation(libs.core.ktx)
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
        iosTest.dependencies {
            implementation(projects.miniapp.testkit)
        }
    }
}

aboutLibraries {
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        prettyPrint = true
    }
}

tasks.named("copyNonXmlValueResourcesForCommonMain") {
    dependsOn("exportLibraryDefinitions")
}

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        // This common parser test calls Android framework stubs and is covered by native tests.
        filter {
            excludeTestsMatching("ge.yet.game.ComposeLibrariesProviderTest")
            // Compose UI's Android host environment requires a Robolectric runner;
            // this common suite executes on the iOS simulator in allTests.
            excludeTestsMatching("ge.yet.game.screen.root.RootContentTest")
        }
    }
}
