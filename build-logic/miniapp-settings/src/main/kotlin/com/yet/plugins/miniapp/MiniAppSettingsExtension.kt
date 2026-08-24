package com.yet.plugins.miniapp

import javax.inject.Inject

abstract class MiniAppSettingsExtension @Inject constructor(
    private val discoveredPaths: Set<String>,
) {
    private val declarations = mutableListOf<MiniAppDeclaration>()
    private var sealed = false

    fun include(projectPath: String, expectedId: String) {
        require(!sealed) { "Mini-app declarations are already finalized" }
        require(projectPath.startsWith(":")) { "Mini-app project path must start with ':'" }
        require(projectPath in discoveredPaths) { "$projectPath is not a discovered mini-app project" }
        MiniAppIdSyntax.requireValid(expectedId)
        require(declarations.none { it.projectPath == projectPath }) {
            "Duplicate mini-app project path: $projectPath"
        }
        require(declarations.none { it.expectedId == expectedId }) {
            "Duplicate mini-app id: $expectedId"
        }
        declarations += MiniAppDeclaration(projectPath, expectedId)
    }

    internal fun sealAndSnapshot(): List<MiniAppDeclaration> {
        sealed = true
        return declarations.toList()
    }
}
