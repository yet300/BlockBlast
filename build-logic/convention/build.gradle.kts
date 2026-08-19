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
        register("miniApp") {
            id = "logica.miniapp"
            implementationClass = "com.yet.plugins.miniapp.MiniAppConventionPlugin"
        }
        register("miniAppRoot") {
            id = "logica.miniapp.root"
            implementationClass = "com.yet.plugins.miniapp.MiniAppRootPlugin"
        }
    }
}

group = "com.yet.buildlogic"

evaluationDependsOn(":miniapp-settings")

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.metro.plugin)

    compileOnly(projects.miniappSettings)

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(projects.miniappSettings)
    testRuntimeOnly(libs.android.gradlePlugin)
    testRuntimeOnly(libs.kotlin.gradlePlugin)
    testRuntimeOnly(libs.kotlin.serialization)
    testRuntimeOnly(libs.compose.compiler.gradle.plugin)
    testRuntimeOnly(libs.compose.gradlePlugin)
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
    systemProperty("sourceRepositoryRoot", rootProject.projectDir.parentFile.absolutePath)
    inputs.dir(rootProject.projectDir.parentFile.resolve("miniapp/api/src/commonMain"))
    inputs.dir(rootProject.projectDir.parentFile.resolve("miniapp/compose/src/commonMain"))
    inputs.dir(rootProject.projectDir.parentFile.resolve("miniapp/metro/src/commonMain"))
    inputs.dir(rootProject.projectDir.parentFile.resolve("miniapp/testkit/src/commonMain"))
}
