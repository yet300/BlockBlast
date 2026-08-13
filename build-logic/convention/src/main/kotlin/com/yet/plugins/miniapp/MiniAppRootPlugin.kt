package com.yet.plugins.miniapp

import org.gradle.api.Plugin
import org.gradle.api.Project

class MiniAppRootPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        tasks.register("createMiniApp", CreateMiniAppTask::class.java) {
            repositoryRoot.set(layout.projectDirectory)
            miniAppId.set(providers.gradleProperty("miniAppId"))
            miniAppName.set(providers.gradleProperty("miniAppName"))
            miniAppProjectPath.set(providers.gradleProperty("miniAppProjectPath"))
        }
        Unit
    }
}
