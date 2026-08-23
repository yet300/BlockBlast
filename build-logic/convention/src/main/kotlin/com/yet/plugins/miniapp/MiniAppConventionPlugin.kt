package com.yet.plugins.miniapp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MiniAppConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        pluginManager.apply("com.plugins.kotlinMultiplatformPlugin")
        pluginManager.apply("com.plugins.composeMultiplatform")
        pluginManager.apply("dev.zacsweers.metro")

        configureResources(extensions.getByName("compose") as ExtensionAware, MiniAppResourcePackage.from(path))
        configureAndroidResources(extensions.getByName("kotlin") as ExtensionAware)
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                api(project(":miniapp:metro"))
                implementation(project(":miniapp:audio-presets"))
                implementation(catalog.findLibrary("compose-components-resources").get())
                implementation(catalog.findLibrary("decompose-compose").get())
            }
            sourceSets.getByName("commonTest").dependencies { implementation(project(":miniapp:testkit")) }
        }
        val projectPath = path
        val validate = tasks.register("validateMiniAppDependencies", ValidateMiniAppDependenciesTask::class.java) {
            violations.empty()
            sourceFiles.from(fileTree("src") { include("**/*.kt") })
            sourceRootPath.set(layout.projectDirectory.asFile.absolutePath)
            miniAppProjectPath.set(projectPath)
        }
        val configurationContainer = configurations
        gradle.projectsEvaluated {
            val messages = configurationContainer.toList()
                .filter { it.isCanBeDeclared }
                .flatMap { configuration ->
                    val projectViolations = configuration.dependencies
                        .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                        .mapNotNull { dependency ->
                            MiniAppDependencyBoundary.violationFor(
                                projectPath,
                                configuration.name,
                                dependency.path,
                            )?.message()
                        }
                    val externalViolations = configuration.dependencies
                        .withType(org.gradle.api.artifacts.ExternalModuleDependency::class.java)
                        .mapNotNull { dependency ->
                            MiniAppDependencyBoundary.externalViolationFor(
                                projectPath = projectPath,
                                configuration = configuration.name,
                                group = dependency.group,
                                name = dependency.name,
                            )?.message()
                        }
                    projectViolations + externalViolations
                }
                .distinct()
                .sorted()
            validate.configure { violations.set(messages) }
        }
        tasks.matching { it.name == "check" || it.name == "allTests" }.configureEach { dependsOn(validate) }
    }

    private fun configureResources(compose: ExtensionAware, resourcePackage: String) {
        val resources = requireNotNull(compose.extensions.findByName("resources")) {
            "Compose resources extension is unavailable"
        }
        resources.javaClass.getMethod("setPublicResClass", Boolean::class.javaPrimitiveType).invoke(resources, false)
        resources.javaClass.getMethod("setPackageOfResClass", String::class.java).invoke(resources, resourcePackage)
    }

    private fun configureAndroidResources(kotlin: ExtensionAware) {
        val android = requireNotNull(kotlin.extensions.findByName("android")) {
            "Kotlin Android library extension is unavailable"
        }
        val resources = android.javaClass.getMethod("getAndroidResources").invoke(android)
        resources.javaClass.getMethod("setEnable", Boolean::class.javaPrimitiveType).invoke(resources, true)
    }
}
