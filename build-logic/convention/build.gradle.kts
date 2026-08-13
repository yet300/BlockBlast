import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatform") {
            id = "com.plugins.kotlinMultiplatformPlugin"
            implementationClass = "com.yet.plugins.KotlinMultiplatformPlugin"
        }
        register("composeMultiplatform") {
            id = "com.plugins.composeMultiplatform"
            implementationClass = "com.yet.plugins.ComposeMultiplatformPlugin"
        }
        register("miniAppBundle") {
            id = "logica.miniapp.bundle"
            implementationClass = "com.yet.plugins.miniapp.MiniAppBundlePlugin"
        }
    }
}

group = "com.yet.buildlogic"

evaluationDependsOn(":miniapp-settings")

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)

    compileOnly(project(":miniapp-settings"))

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(project(":miniapp-settings"))
    testRuntimeOnly(libs.android.gradlePlugin)
    testRuntimeOnly(libs.kotlin.gradlePlugin)
    testRuntimeOnly(libs.kotlin.serialization)
    testRuntimeOnly(libs.metro.plugin)
}

tasks.withType<Test>().configureEach {
    val conventionPluginClasspath = tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata")
        .flatMap { it.outputDirectory.file("plugin-under-test-metadata.properties") }
    val miniAppSettingsJar = project(":miniapp-settings").tasks.named<Jar>("jar")
        .flatMap(Jar::getArchiveFile)

    dependsOn(conventionPluginClasspath, miniAppSettingsJar)
    inputs.file(conventionPluginClasspath)
    inputs.file(miniAppSettingsJar)
    systemProperty("conventionPluginClasspathFile", conventionPluginClasspath.get().asFile.absolutePath)
    systemProperty("miniAppSettingsJar", miniAppSettingsJar.get().asFile.absolutePath)
}
