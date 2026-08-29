package com.yet.plugins.miniapp

import java.io.File

internal class MiniAppScaffoldRenderer(
    private val id: String,
    private val displayName: String,
    private val projectPath: String,
) {
    private val segments = id.split('.').also { require(it.all(Regex("^[a-z][a-z0-9]*$")::matches)) }
    private val packageName = "ge.yet.${segments.joinToString(".")}"
    private val resourcePackage = MiniAppResourcePackage.from(projectPath)
    private val classPrefix = segments.last().replaceFirstChar(Char::uppercaseChar)
    private val docsPath = if (projectPath.startsWith(":game:")) "../../docs" else "../../../docs"
    private val graphFactoryMethod = "create" +
        segments.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } +
        "SessionGraph"

    fun writeTo(root: File) {
        write(root, "build.gradle.kts", "plugins { id(\"logica.miniapp\") }\n")
        write(root, "AGENTS.md", """
            # $classPrefix MiniApp

            Read [the AI contributor protocol]($docsPath/miniapp/AI_CONTRIBUTOR_PROTOCOL.md)
            before making changes. The human workflow is documented in
            [the MiniApp contributor guide]($docsPath/CONTRIBUTING_MINIAPP.md).

            Use `MiniAppId("$id").storageKey(localName)` for every new persistent key. Never copy another plugin's key prefix.
            This project is discovered on the next Gradle invocation, but is not shipped until a maintainer adds it to the production allowlist.
            Verify it with `./gradlew :${projectPath.removePrefix(":").replace(':', ':')}:verifyMiniApp`.
        """.trimIndent() + "\n")
        write(root, "src/commonMain/composeResources/values/strings.xml", """
            <resources>
                <string name="miniapp_title">${xml(displayName)}</string>
                <string name="miniapp_description">${xml(displayName)} mini-app</string>
            </resources>
        """.trimIndent() + "\n")
        write(root, "src/commonMain/composeResources/drawable/miniapp_icon.xml", """
            <vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
                <path android:fillColor="#FF6750A4" android:pathData="M3,3h18v18h-18z" />
            </vector>
        """.trimIndent() + "\n")
        write(root, "src/commonMain/kotlin/${packageName.replace('.', '/')}/${classPrefix}Component.kt", """
            package $packageName

            import com.arkivanov.decompose.ComponentContext
            import com.arkivanov.essenty.lifecycle.doOnDestroy

            interface ${classPrefix}Component

            internal class Default${classPrefix}Component(componentContext: ComponentContext) : ${classPrefix}Component,
                ComponentContext by componentContext {
                init { componentContext.lifecycle.doOnDestroy { } }
            }
        """.trimIndent() + "\n")
        write(root, "src/commonMain/kotlin/${packageName.replace('.', '/')}/${classPrefix}Content.kt", """
            package $packageName

            import androidx.compose.foundation.layout.Box
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            @Composable
            internal fun ${classPrefix}Content(component: ${classPrefix}Component, modifier: Modifier = Modifier) {
                Box(modifier = modifier)
            }
        """.trimIndent() + "\n")
        write(root, "src/commonMain/kotlin/${packageName.replace('.', '/')}/${classPrefix}Session.kt", """
            package $packageName

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import ge.yet.game.miniapp.compose.MiniAppSession

            class ${classPrefix}Session internal constructor(
                private val component: ${classPrefix}Component,
            ) : MiniAppSession {
                @Composable
                override fun Content(modifier: Modifier) {
                    ${classPrefix}Content(component = component, modifier = modifier)
                }
            }
        """.trimIndent() + "\n")
        write(root, "src/commonMain/kotlin/${packageName.replace('.', '/')}/${classPrefix}SessionGraph.kt", graphSource())
        write(root, "src/commonMain/kotlin/${packageName.replace('.', '/')}/${classPrefix}Plugin.kt", pluginSource())
        write(root, "src/commonTest/kotlin/${packageName.replace('.', '/')}/${classPrefix}PluginContractTest.kt", """
            package $packageName

            import dev.zacsweers.metro.AppScope
            import dev.zacsweers.metro.DependencyGraph
            import dev.zacsweers.metro.createGraph
            import ge.yet.game.miniapp.api.MiniAppId
            import ge.yet.game.miniapp.audio.presets.PlacementClick
            import ge.yet.game.miniapp.compose.MiniAppRegistry
            import ge.yet.game.miniapp.metro.MiniAppMetroBindings
            import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
            import ge.yet.game.miniapp.testkit.withMiniAppSession
            import kotlin.test.Test
            import kotlin.test.assertNotNull

            @DependencyGraph(
                scope = AppScope::class,
                bindingContainers = [MiniAppMetroBindings::class],
            )
            interface ${classPrefix}PluginTestGraph {
                val registry: MiniAppRegistry
            }

            class ${classPrefix}PluginContractTest {
                @Test
                fun `isolated graph contains exactly this plugin`() {
                    val expectedId = MiniAppId("$id")
                    val graph = createGraph<${classPrefix}PluginTestGraph>()

                    MiniAppContractAssertions.assertSinglePlugin(graph.registry, expectedId)
                    val plugin = assertNotNull(graph.registry[expectedId])
                    MiniAppContractAssertions.assertManifest(plugin, expectedId)
                }

                @Test
                fun `plugin creates a graph retained session`() {
                    val expectedId = MiniAppId("$id")
                    val graph = createGraph<${classPrefix}PluginTestGraph>()
                    val plugin = assertNotNull(graph.registry[expectedId])
                    withMiniAppSession { harness ->
                        val sharedSfx = PlacementClick()
                        assertNotNull(harness.context.audio)
                        assertNotNull(sharedSfx)
                        val session = plugin.createSession(harness.context)
                        MiniAppContractAssertions.assertRetainedGraphSession(session)
                        harness.resume()
                    }
                }
            }
        """.trimIndent() + "\n")
    }

    private fun graphSource() = """
        package $packageName

        import com.arkivanov.decompose.ComponentContext
        import dev.zacsweers.metro.AppScope
        import dev.zacsweers.metro.ContributesTo
        import dev.zacsweers.metro.GraphExtension
        import dev.zacsweers.metro.Provides
        import dev.zacsweers.metro.SingleIn
        import ge.yet.game.miniapp.compose.MiniAppSessionContext
        import ge.yet.game.miniapp.metro.MiniAppSessionScope

        @GraphExtension(MiniAppSessionScope::class)
        interface ${classPrefix}SessionGraph {
            val session: ${classPrefix}Session

            @Provides @SingleIn(MiniAppSessionScope::class)
            fun provideComponent(componentContext: ComponentContext): ${classPrefix}Component = Default${classPrefix}Component(componentContext)

            @Provides @SingleIn(MiniAppSessionScope::class)
            fun provideSession(component: ${classPrefix}Component): ${classPrefix}Session = ${classPrefix}Session(component)

            @ContributesTo(AppScope::class)
            @GraphExtension.Factory
            fun interface Factory {
                fun $graphFactoryMethod(
                    @Provides context: MiniAppSessionContext,
                ): ${classPrefix}SessionGraph
            }
        }
    """.trimIndent() + "\n"

    private fun pluginSource() = """
        package $packageName

        import dev.zacsweers.metro.AppScope
        import dev.zacsweers.metro.ContributesIntoSet
        import dev.zacsweers.metro.Inject
        import ge.yet.game.miniapp.api.MiniAppCategoryId
        import ge.yet.game.miniapp.api.MiniAppId
        import ge.yet.game.miniapp.compose.MiniAppManifest
        import ge.yet.game.miniapp.compose.MiniAppPlugin
        import ge.yet.game.miniapp.compose.MiniAppSession
        import ge.yet.game.miniapp.compose.MiniAppSessionContext
        import ge.yet.game.miniapp.metro.RetainedMiniAppSession
        import $resourcePackage.Res
        import $resourcePackage.miniapp_description
        import $resourcePackage.miniapp_icon
        import $resourcePackage.miniapp_title

        @Inject
        @ContributesIntoSet(AppScope::class)
        class ${classPrefix}Plugin(
            private val graphFactory: ${classPrefix}SessionGraph.Factory,
        ) : MiniAppPlugin {
            override val manifest = MiniAppManifest(
                id = MiniAppId("$id"), title = Res.string.miniapp_title, description = Res.string.miniapp_description,
                icon = Res.drawable.miniapp_icon, cover = null, category = MiniAppCategoryId("${segments.first()}"), sortPriority = 0,
            )
            override fun createSession(context: MiniAppSessionContext): MiniAppSession {
                val graph = graphFactory.$graphFactoryMethod(context)
                return RetainedMiniAppSession(graph, graph.session)
            }
        }
    """.trimIndent() + "\n"

    private fun write(root: File, relativePath: String, content: String) {
        root.resolve(relativePath).also { it.parentFile.mkdirs(); it.writeText(content) }
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
