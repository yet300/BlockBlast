package com.yet.plugins.miniapp

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.create

class MiniAppSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val discovered = discoverMiniAppProjectPaths(settings.rootDir)

        discovered.forEach(settings::include)
        val model = settings.gradle.extensions.create<MiniAppShippingModel>("miniAppShippingModel")
        val extension = settings.extensions.create<MiniAppSettingsExtension>(
            "miniApps", discovered.toSet(),
        )
        settings.gradle.settingsEvaluated {
            model.declarations.set(extension.sealAndSnapshot())
            model.declarations.finalizeValue()
        }
    }
}
