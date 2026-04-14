plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatform") {
            id = "com.plugins.kotlinMultiplatformPlugin"
            implementationClass = "com.yet.plugins.KotlinMultiplatformPlugin"
        }
    }
}

group = "com.yet.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}
