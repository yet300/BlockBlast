package com.yet.plugins.miniapp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class MiniAppRootPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        tasks.register("createMiniApp", CreateMiniAppTask::class.java) {
            repositoryRoot.set(layout.projectDirectory)
            miniAppId.set(providers.gradleProperty("miniAppId"))
            miniAppName.set(providers.gradleProperty("miniAppName"))
            miniAppProjectPath.set(providers.gradleProperty("miniAppProjectPath"))
            miniAppProfile.set(providers.gradleProperty("miniAppProfile"))
        }
        val shippingModel = gradle.extensions.getByType<MiniAppShippingModel>()
        val verifyMiniApp = tasks.register("verifyMiniApp") {
            group = "verification"
            description = "Verifies the MiniApp bundle and every allowlisted MiniApp."
            dependsOn(":miniapp:bundle:verifyMiniAppBundle")
            shippingModel.declarations.get().forEach { declaration ->
                dependsOn("${declaration.projectPath}:verifyMiniApp")
            }
        }
        tasks.register("miniAppBaseline") {
            group = "verification"
            description = "Alias for verifyMiniApp."
            dependsOn(verifyMiniApp)
        }
        Unit
    }
}
