package com.yet.plugins.miniapp

import java.io.File

internal fun discoverMiniAppProjectPaths(rootDir: File): List<String> =
    listOf("game", "miniapp/samples")
        .flatMap { root ->
            rootDir.resolve(root).listFiles().orEmpty()
                .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
                .map { directory -> ":${root.replace('/', ':')}:${directory.name}" }
        }
        .sorted()
