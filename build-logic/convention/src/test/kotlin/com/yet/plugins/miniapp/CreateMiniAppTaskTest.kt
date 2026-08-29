package com.yet.plugins.miniapp

import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains

class CreateMiniAppTaskTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `game id renders exact source names and escaped display name`() {
        val target = temporaryFolder.newFolder("snake")
        MiniAppScaffoldRenderer("game.snake", "Snake & <Friends> \"'", ":game:snake").writeTo(target)
        assertEquals(
            setOf(
                "AGENTS.md", "build.gradle.kts", "src/commonMain/composeResources/drawable/miniapp_icon.xml",
                "src/commonMain/composeResources/values/strings.xml", "src/commonMain/kotlin/ge/yet/game/snake/SnakeComponent.kt",
                "src/commonMain/kotlin/ge/yet/game/snake/SnakeContent.kt", "src/commonMain/kotlin/ge/yet/game/snake/SnakePlugin.kt",
                "src/commonMain/kotlin/ge/yet/game/snake/SnakeSession.kt", "src/commonMain/kotlin/ge/yet/game/snake/SnakeSessionGraph.kt",
                "src/commonTest/kotlin/ge/yet/game/snake/SnakePluginContractTest.kt",
            ),
            target.walkTopDown().filter(File::isFile).map { it.relativeTo(target).invariantSeparatorsPath }.toSet(),
        )
        assertEquals(true, target.resolve("src/commonMain/composeResources/values/strings.xml").readText().contains("Snake &amp; &lt;Friends&gt; &quot;&apos;"))
        assertContains(
            target.resolve("AGENTS.md").readText(),
            "MiniAppId(\"game.snake\").storageKey(localName)",
        )
        assertEquals("plugins { id(\"logica.miniapp\") }\n", target.resolve("build.gradle.kts").readText())
        val plugin = target.resolve("src/commonMain/kotlin/ge/yet/game/snake/SnakePlugin.kt").readText()
        assertEquals(true, plugin.contains("import ge.yet.game.snake.generated.resources.miniapp_title"))
        assertEquals(true, plugin.contains("import ge.yet.game.snake.generated.resources.miniapp_description"))
        assertEquals(true, plugin.contains("import ge.yet.game.snake.generated.resources.miniapp_icon"))
        assertContains(plugin, "RetainedMiniAppSession(graph, graph.session)")
        assertContains(plugin, "graphFactory.createGameSnakeSessionGraph(")
        val sessionGraph = target.resolve("src/commonMain/kotlin/ge/yet/game/snake/SnakeSessionGraph.kt").readText()
        assertContains(sessionGraph, "val session: SnakeSession")
        assertContains(sessionGraph, "fun provideSession(component: SnakeComponent): SnakeSession")
        assertContains(sessionGraph, "fun createGameSnakeSessionGraph(")
        assertEquals(false, sessionGraph.contains("Named"))
        val session = target.resolve("src/commonMain/kotlin/ge/yet/game/snake/SnakeSession.kt").readText()
        assertContains(session, "class SnakeSession internal constructor(")
        assertEquals(false, session.contains("internal class SnakeSession"))
        assertContains(session, "SnakeContent(component = component, modifier = modifier)")
        val content = target.resolve("src/commonMain/kotlin/ge/yet/game/snake/SnakeContent.kt").readText()
        assertContains(content, "Box(modifier = modifier)")
        val component = target.resolve("src/commonMain/kotlin/ge/yet/game/snake/SnakeComponent.kt").readText()
        assertContains(component, "componentContext.lifecycle.doOnDestroy")
        assertContains(target.resolve("AGENTS.md").readText(), "not shipped until a maintainer adds it to the production allowlist")
        assertContains(target.resolve("AGENTS.md").readText(), "docs/miniapp/AI_CONTRIBUTOR_PROTOCOL.md")
        assertContains(target.resolve("AGENTS.md").readText(), ":game:snake:verifyMiniApp")
    }

    @Test
    fun `sample resources derive from project path rather than id`() {
        val target = temporaryFolder.newFolder("counter")
        MiniAppScaffoldRenderer("sample.counter", "Counter", ":miniapp:samples:counter").writeTo(target)
        assertEquals(
            true,
            target.resolve("src/commonMain/kotlin/ge/yet/sample/counter/CounterPlugin.kt").readText()
                .contains("import ge.yet.miniapp.samples.counter.generated.resources.Res"),
        )
        assertContains(
            target.resolve("AGENTS.md").readText(),
            "../../../docs/miniapp/AI_CONTRIBUTOR_PROTOCOL.md",
        )
    }

    @Test
    fun `generated contract owns an isolated final graph and checks its only plugin`() {
        val target = temporaryFolder.newFolder("snake-contract")
        MiniAppScaffoldRenderer("game.snake", "Snake", ":game:snake").writeTo(target)

        val contract = target.resolve(
            "src/commonTest/kotlin/ge/yet/game/snake/SnakePluginContractTest.kt",
        ).readText()

        assertContains(contract, "@DependencyGraph(")
        assertContains(contract, "scope = AppScope::class")
        assertContains(contract, "bindingContainers = [MiniAppMetroBindings::class]")
        assertContains(contract, "createGraph<SnakePluginTestGraph>()")
        assertContains(contract, "MiniAppContractAssertions.assertSinglePlugin")
        assertContains(contract, "MiniAppContractAssertions.assertManifest")
        assertContains(contract, "MiniAppContractAssertions.assertRetainedGraphSession")
        assertContains(contract, "import ge.yet.game.miniapp.audio.presets.PlacementClick")
        assertContains(contract, "val sharedSfx = PlacementClick()")
        assertContains(contract, "import ge.yet.game.miniapp.testkit.withMiniAppSession")
        assertContains(contract, "assertNotNull(harness.context.audio)")
        assertEquals(false, contract.contains("playSfx("))
    }

    @Test
    fun `root task creates a discoverable but unshipped game under strict configuration cache`() {
        val fixture = MiniAppBundleGradleTestProject(temporaryFolder, declarations = "", useMarker = false)
        fixture.write("build.gradle.kts", """
            plugins { id("logica.miniapp.root") }
            allprojects { repositories { google(); mavenCentral() } }
        """)
        fixture.run(
            "createMiniApp", "-PminiAppId=game.snake", "-PminiAppName=Snake",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        fixture.run(":verifyMiniApp", "--configuration-cache", "--configuration-cache-problems=fail")
        val projects = fixture.run("projects")
        assertContains(projects.output, ":game:snake")
        val bundle = fixture.run(":miniapp:bundle:verifyMiniAppBundle")
        assertContains(bundle.output, "BUILD SUCCESSFUL")
        val dependencies = fixture.run(":miniapp:bundle:dependencies", "--configuration", "commonMainApi")
        assertContains(dependencies.output, "project metro")
        assertEquals(false, dependencies.output.contains("project snake"))
        fixture.run(":miniapp:bundle:generateProductionMiniApps")
        assertEquals(false, fixture.generatedExpectation().contains("SnakePlugin"))
    }

    @Test
    fun `root create task action is configuration-cache reusable`() {
        val fixture = MiniAppBundleGradleTestProject(temporaryFolder, declarations = "", useMarker = false)
        fixture.write("build.gradle.kts", "plugins { id(\"logica.miniapp.root\") }")
        fixture.write("game/snake/build.gradle.kts", "plugins { base }")
        fixture.write("game/snake/keep.txt", "keep")
        val first = fixture.runAndFail(
            "createMiniApp", "-PminiAppId=game.snake", "-PminiAppName=Snake",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        val second = fixture.runAndFail(
            "createMiniApp", "-PminiAppId=game.snake", "-PminiAppName=Snake",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        assertContains(first.output, "Refusing to overwrite existing mini-app project :game:snake")
        assertContains(second.output, "Reusing configuration cache")
        assertEquals("keep", fixture.read("game/snake/keep.txt"))
        assertEquals(false, fixture.hasStagingDirectory("game", "snake"))
    }

    @Test
    fun `generated game compiles against real miniapp contracts on the next invocation`() {
        val fixture = MiniAppBundleGradleTestProject(temporaryFolder, declarations = "", useMarker = false)
        fixture.copyRealMiniAppContracts()
        fixture.write("build.gradle.kts", """
            plugins { id("logica.miniapp.root") }
            allprojects { repositories { google(); mavenCentral() } }
        """)
        fixture.run(
            "createMiniApp", "-PminiAppId=game.snake", "-PminiAppName=Snake",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        val compile = fixture.run(
            ":game:snake:compileKotlinIosSimulatorArm64",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        assertContains(compile.output, "BUILD SUCCESSFUL")
        val tests = fixture.run(
            ":game:snake:compileTestKotlinIosSimulatorArm64",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )
        assertContains(tests.output, "BUILD SUCCESSFUL")
    }

    @Test
    fun `generated game compiles Android when the required JDK17 toolchain is installed`() {
        val fixture = MiniAppBundleGradleTestProject(temporaryFolder, declarations = "", useMarker = false)
        fixture.copyRealMiniAppContracts()
        fixture.write("build.gradle.kts", """
            plugins { id("logica.miniapp.root") }
            allprojects { repositories { google(); mavenCentral() } }
        """)
        fixture.run(
            "createMiniApp", "-PminiAppId=game.snake", "-PminiAppName=Snake",
            "--configuration-cache", "--configuration-cache-problems=fail",
        )

        try {
            assertContains(
                fixture.run(
                    ":game:snake:compileAndroidMain",
                    "--configuration-cache", "--configuration-cache-problems=fail",
                ).output,
                "BUILD SUCCESSFUL",
            )
        } catch (failure: UnexpectedBuildFailure) {
            if (failure.message.orEmpty().contains("Cannot find a Java installation") &&
                failure.message.orEmpty().contains("languageVersion=17")) {
                org.junit.Assume.assumeTrue("JDK 17 toolchain is unavailable on this host", false)
            }
            throw failure
        }
    }

    @Test
    fun `generated sample compiles against real contracts with its explicit resource package`() {
        val fixture = MiniAppBundleGradleTestProject(temporaryFolder, declarations = "", useMarker = false)
        fixture.copyRealMiniAppContracts()
        fixture.write("build.gradle.kts", """
            plugins { id("logica.miniapp.root") }
            allprojects { repositories { google(); mavenCentral() } }
        """)
        fixture.run(
            "createMiniApp", "-PminiAppId=sample.counter", "-PminiAppName=Counter",
            "-PminiAppProjectPath=:miniapp:samples:counter",
        )
        val compile = fixture.run(":miniapp:samples:counter:compileCommonMainKotlinMetadata")
        assertContains(compile.output, "BUILD SUCCESSFUL")
        val tests = fixture.run(":miniapp:samples:counter:compileTestKotlinIosSimulatorArm64")
        assertContains(tests.output, "BUILD SUCCESSFUL")
    }

    @Test
    fun `throwing renderer removes its sibling staging directory`() {
        val target = temporaryFolder.root.resolve("game/snake")
        assertFailsWith<IllegalStateException> {
            createMiniAppWithoutReplacing(target) { throw IllegalStateException("render failed") }
        }
        assertEquals(false, target.exists())
        assertEquals(false, target.parentFile.listFiles().orEmpty().any { it.name.startsWith(".snake.staging-") })
    }

    @Test
    fun `publication never replaces a target created while the scaffold is rendering`() {
        val parent = temporaryFolder.newFolder("game")
        val target = parent.resolve("snake")
        assertFailsWith<IllegalStateException> {
            createMiniAppWithoutReplacing(target) { staging ->
                staging.resolve("generated.txt").writeText("generated")
                target.mkdirs()
                target.resolve("sentinel.txt").writeText("keep")
            }
        }
        assertEquals("keep", target.resolve("sentinel.txt").readText())
        assertEquals(false, parent.listFiles().orEmpty().any { it.name.startsWith(".snake.staging-") })
    }

    @Test
    fun `path traversal is rejected before target construction`() {
        assertFailsWith<IllegalArgumentException> { validatedProjectDirectory(temporaryFolder.root, ":game:../snake") }
    }

    @Test
    fun `symlinked project parent is rejected before external writes or staging`() {
        val external = temporaryFolder.newFolder("external")
        val game = temporaryFolder.root.toPath().resolve("game")
        try {
            Files.createSymbolicLink(game, external.toPath())
        } catch (failure: UnsupportedOperationException) {
            org.junit.Assume.assumeNoException(failure)
        } catch (failure: java.nio.file.FileSystemException) {
            org.junit.Assume.assumeNoException(failure)
        }

        assertFailsWith<IllegalArgumentException> { validatedProjectDirectory(temporaryFolder.root, ":game:snake") }
        assertEquals(false, external.resolve("snake").exists())
        assertEquals(false, external.listFiles().orEmpty().any { it.name.startsWith(".snake.staging-") })
    }

    @Test
    fun `root task rejects malformed id blank name and traversal without source or staging writes`() {
        val fixture = MiniAppBundleGradleTestProject(temporaryFolder, declarations = "", useMarker = false)
        fixture.write("build.gradle.kts", "plugins { id(\"logica.miniapp.root\") }")

        val malformed = fixture.runAndFail(
            "createMiniApp", "-PminiAppId=game.Snake", "-PminiAppName=Snake",
        )
        val blankName = fixture.runAndFail(
            "createMiniApp", "-PminiAppId=game.snake", "-PminiAppName=",
        )
        val traversal = fixture.runAndFail(
            "createMiniApp", "-PminiAppId=game.snake", "-PminiAppName=Snake", "-PminiAppProjectPath=:game:../snake",
        )

        assertContains(malformed.output, "Invalid mini-app id")
        assertContains(blankName.output, "miniAppName must not be blank")
        assertContains(traversal.output, "Mini-app project path must be")
        assertEquals(false, fixture.exists("game/snake"))
        assertEquals(false, fixture.hasStagingDirectory("game", "snake"))
    }
}
