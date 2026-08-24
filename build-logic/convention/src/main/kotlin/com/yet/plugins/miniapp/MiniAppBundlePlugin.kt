package com.yet.plugins.miniapp

import com.yet.plugins.KotlinMultiplatformPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

public class MiniAppBundlePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(KotlinMultiplatformPlugin::class.java)
        pluginManager.apply("dev.zacsweers.metro")

        val model = gradle.extensions.getByType<MiniAppShippingModel>()
        val shippingDeclarations = model.declarations.get()
        shippingDeclarations.forEach { evaluationDependsOn(it.projectPath) }

        val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
        kotlin.sourceSets.getByName("commonMain").dependencies {
            api(dependencies.project(mapOf("path" to ":miniapp:metro")))
            shippingDeclarations.forEach { declaration ->
                api(dependencies.project(mapOf("path" to declaration.projectPath)))
            }
        }

        val generatedOutputDirectory = layout.buildDirectory.dir("generated/miniapps/commonMain/kotlin")
        val generate = tasks.register(
            "generateProductionMiniApps",
            GenerateProductionMiniAppsTask::class.java,
        ) {
            this.declarations.set(shippingDeclarations)
            outputDirectory.set(generatedOutputDirectory)
        }
        kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generate)

        val verify = tasks.register(
            "verifyMiniAppBundle",
            VerifyMiniAppBundleTask::class.java,
        )
        afterEvaluate {
            val expected = shippingDeclarations.map(MiniAppDeclaration::projectPath)
            val actual = directCommonMainProjectPaths().filterNot { it == ":miniapp:metro" }
            val convention = expected.filter { project(it).pluginManager.hasPlugin("logica.miniapp") }
            verify.configure {
                expectedProjectPaths.set(expected)
                actualProjectPaths.set(actual)
                conventionProjectPaths.set(convention)
            }
        }
        tasks.matching { it.name == "check" || it.name == "allTests" }.configureEach {
            dependsOn(verify)
        }
    }

    private fun Project.directCommonMainProjectPaths(): List<String> =
        configurations.getByName("commonMainApi").dependencies
            .filterIsInstance<ProjectDependency>()
            .map(ProjectDependency::getPath)
}
