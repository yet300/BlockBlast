package com.yet.plugins.miniapp

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets.UTF_8
import javax.inject.Inject

@CacheableTask
internal abstract class GenerateProductionMiniAppsTask : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Input
    abstract val declarations: ListProperty<MiniAppDeclaration>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        fileSystemOperations.delete {
            delete(outputDirectory)
        }
        val ids = declarations.get().map(MiniAppDeclaration::expectedId)
        val body = ids.joinToString("\n") { "        MiniAppId(\"$it\")," }
        val file = outputDirectory.file(
            "ge/yet/game/miniapp/bundle/ProductionMiniAppExpectation.kt",
        ).get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package ge.yet.game.miniapp.bundle\n" +
                "import dev.zacsweers.metro.AppScope\n" +
                "import dev.zacsweers.metro.ContributesIntoSet\n" +
                "import dev.zacsweers.metro.Inject\n" +
                "import ge.yet.game.miniapp.api.MiniAppId\n" +
                "import ge.yet.game.miniapp.metro.MiniAppRegistryExpectation\n" +
                "@Inject\n" +
                "@ContributesIntoSet(AppScope::class)\n" +
                "public class ProductionMiniAppExpectation : MiniAppRegistryExpectation {\n" +
                " override val expectedIds: Set<MiniAppId> = linkedSetOf(\n" +
                "$body\n" +
                " )\n" +
                "}\n",
            UTF_8,
        )
    }
}
