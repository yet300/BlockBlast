package ge.yet.game.miniapp.testkit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.metro.DefaultMiniAppRegistry
import ge.yet.game.miniapp.metro.RetainedMiniAppSession
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(InternalResourceApi::class)
class MiniAppContractAssertionsTest {
    @Test
    fun `single plugin assertion accepts the expected contribution`() {
        val expectedId = MiniAppId("sample.fixture")
        val plugin = FakePlugin(expectedId)
        val registry = DefaultMiniAppRegistry(setOf(plugin), emptySet())

        MiniAppContractAssertions.assertSinglePlugin(registry, expectedId)
        MiniAppContractAssertions.assertManifest(plugin, expectedId)
    }

    @Test
    fun `single plugin assertion rejects an empty registry`() {
        val registry = DefaultMiniAppRegistry(emptySet(), emptySet())

        assertFailsWith<AssertionError> {
            MiniAppContractAssertions.assertSinglePlugin(
                registry = registry,
                expectedId = MiniAppId("sample.fixture"),
            )
        }
    }

    @Test
    fun `single plugin assertion rejects an extra contribution`() {
        val registry = DefaultMiniAppRegistry(
            plugins = setOf(
                FakePlugin(MiniAppId("sample.fixture")),
                FakePlugin(MiniAppId("sample.other")),
            ),
            expectations = emptySet(),
        )

        assertFailsWith<AssertionError> {
            MiniAppContractAssertions.assertSinglePlugin(
                registry = registry,
                expectedId = MiniAppId("sample.fixture"),
            )
        }
    }

    @Test
    fun `single plugin assertion rejects the wrong expected id`() {
        val registry = DefaultMiniAppRegistry(
            plugins = setOf(FakePlugin(MiniAppId("sample.fixture"))),
            expectations = emptySet(),
        )

        assertFailsWith<AssertionError> {
            MiniAppContractAssertions.assertSinglePlugin(
                registry = registry,
                expectedId = MiniAppId("sample.other"),
            )
        }
    }

    @Test
    fun `manifest assertion rejects a mismatched id`() {
        val plugin = FakePlugin(MiniAppId("sample.fixture"))

        assertFailsWith<AssertionError> {
            MiniAppContractAssertions.assertManifest(
                plugin = plugin,
                expectedId = MiniAppId("sample.other"),
            )
        }
    }

    @Test
    fun `retained session assertion rejects a bare session`() {
        assertFailsWith<AssertionError> {
            MiniAppContractAssertions.assertRetainedGraphSession(BareSession)
        }
        MiniAppContractAssertions.assertRetainedGraphSession(
            RetainedMiniAppSession(graph = Any(), delegate = BareSession),
        )
    }

    private class FakePlugin(id: MiniAppId) : MiniAppPlugin {
        override val manifest = MiniAppManifest(
            id = id,
            title = StringResource("test:title", "title", emptySet()),
            description = StringResource("test:description", "description", emptySet()),
            icon = DrawableResource("test:icon", emptySet()),
            cover = null,
            category = MiniAppCategoryId("sample"),
            sortPriority = 0,
        )

        override fun createSession(
            componentContext: ComponentContext,
            visibility: MiniAppVisibilitySource,
            host: MiniAppSessionHost,
        ): MiniAppSession = BareSession
    }

    private data object BareSession : MiniAppSession {
        @Composable
        override fun Content(modifier: Modifier) = Unit
    }
}
