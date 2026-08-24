package com.yet.plugins.miniapp

import java.io.Serializable

data class MiniAppDeclaration(
    val projectPath: String,
    val expectedId: String,
) : Serializable
