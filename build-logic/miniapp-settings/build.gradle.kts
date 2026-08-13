plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("miniAppSettings") {
            id = "logica.miniapp.settings"
            implementationClass = "com.yet.plugins.miniapp.MiniAppSettingsPlugin"
        }
    }
}

group = "com.yet.buildlogic"

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}
