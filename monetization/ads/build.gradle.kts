@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {

    swiftPMDependencies {
        iosMinimumDeploymentTarget.set("15.0")

        swiftPackage(
            url = url("https://github.com/googleads/swift-package-manager-google-mobile-ads.git"),
            version = exact("13.3.0"),
            products = listOf(product("GoogleMobileAds")),
        )
        swiftPackage(
            url = url("https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git"),
            version = exact("3.1.0"),
            products = listOf(product("GoogleUserMessagingPlatform")),
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.monetization.core)

            implementation(libs.basic.ads)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.play.services.ads)
            implementation(libs.user.messaging.platform)
        }
    }
}
