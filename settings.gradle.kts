rootProject.name = "BlockBlast"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("logica.miniapp.settings")
}

miniApps {
    include(
        projectPath = ":game:blockblast",
        expectedId = "game.blockblast",
    )
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":androidApp")


include(":core")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:telemetry")
include(":core:uikit")

include(":monetization")
include(":monetization:core")
include(":monetization:ads")

include(":feature")
include(":feature:root")
include(":feature:settings")
include(":feature:review")
include(":feature:home")
include(":feature:catalog")

include(":game")

include(":miniapp")
include(":miniapp:api")
include(":miniapp:compose")
include(":miniapp:metro")
include(":miniapp:testkit")
include(":miniapp:bundle")
include(":miniapp:integration-test")
