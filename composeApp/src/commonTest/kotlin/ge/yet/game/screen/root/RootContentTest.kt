package ge.yet.game.screen.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.arkivanov.decompose.value.MutableValue
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.screen.miniapp.MiniAppFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RootContentTest {

    @Test
    fun catalog_has_no_banner_frame() = runComposeUiTest {
        setContent {
            RootChildContent(
                child = RootComponent.Child.Catalog(FakeCatalogComponent),
                onBack = {},
                onSettings = {},
                bottomBar = { Box(Modifier.testTag("test_banner")) },
            )
        }

        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithContentDescription("Settings").assertDoesNotExist()
        onNodeWithTag("test_banner").assertDoesNotExist()
        onNodeWithTag("root_ambient_background").assertDoesNotExist()
        onNodeWithTag("catalog_ambient_background").assertIsDisplayed()
        onNodeWithTag("catalog_content").assertExists()
    }

    @Test
    fun running_content_is_bounded_by_host_chrome_and_back_calls_root() = runComposeUiTest {
        val session = TaggingSession()
        var backClicks = 0
        setContent {
            RootChildContent(
                child = running(session),
                onBack = { backClicks += 1 },
                onSettings = {},
                bottomBar = { Box(Modifier.testTag("eligible_banner_content")) },
            )
        }

        onNodeWithTag("catalog_ambient_background").assertDoesNotExist()

        val viewportBounds = onNodeWithTag("session_viewport").getUnclippedBoundsInRoot()
        val backControl = onNodeWithTag("miniapp_back_control")
        val backBounds = backControl.getUnclippedBoundsInRoot()
        val bannerBounds = onNodeWithTag("miniapp_banner_container").getUnclippedBoundsInRoot()

        assertTrue(
            viewportBounds.top >= backBounds.bottom,
            "viewport=$viewportBounds back=$backBounds",
        )
        assertTrue(
            viewportBounds.bottom <= bannerBounds.top,
            "viewport=$viewportBounds banner=$bannerBounds",
        )

        backControl.performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun session_background_covers_the_frame_behind_host_chrome() = runComposeUiTest {
        setContent {
            RootChildContent(
                child = running(BackgroundSession()),
                onBack = {},
                onSettings = {},
                bottomBar = { Box(Modifier.testTag("eligible_banner_content")) },
            )
        }

        val background = onNodeWithTag("session_background").getUnclippedBoundsInRoot()
        val frame = onNodeWithTag("miniapp_frame").getUnclippedBoundsInRoot()
        val back = onNodeWithTag("miniapp_back_control").getUnclippedBoundsInRoot()
        val banner = onNodeWithTag("miniapp_banner_container").getUnclippedBoundsInRoot()

        assertEquals(frame, background)
        assertTrue(background.top <= back.top)
        assertTrue(background.bottom >= banner.bottom)
    }

    @Test
    fun unavailable_content_renders_in_viewport_and_returns_to_catalog() = runComposeUiTest {
        var backClicks = 0
        setContent {
            RootChildContent(
                child = RootComponent.Child.RunningMiniApp(
                    id = MiniAppId("game.missing"),
                    state = RootComponent.MiniAppState.Unavailable(MiniAppId("game.missing")),
                ),
                onBack = { backClicks += 1 },
                onSettings = {},
            )
        }

        onNodeWithText("game.missing is unavailable").assertIsDisplayed()
        onNodeWithText("Back to catalog").assertIsDisplayed().performClick()

        assertEquals(1, backClicks)
    }

    @Test
    fun settings_click_calls_root_without_replacing_session() = runComposeUiTest {
        val session = TaggingSession()
        val child = running(session)
        var settingsClicks = 0
        setContent {
            RootChildContent(
                child = child,
                onBack = {},
                onSettings = { settingsClicks += 1 },
            )
        }

        onNodeWithContentDescription("Settings").performClick()

        assertEquals(1, settingsClicks)
        assertSame(session, (child.state as RootComponent.MiniAppState.Content).session)
    }

    @Test
    fun session_top_bar_slot_shares_the_host_toolbar_row() = runComposeUiTest {
        setContent {
            RootChildContent(
                child = running(TopBarSession()),
                onBack = {},
                onSettings = {},
            )
        }

        val title = onNodeWithTag("session_top_bar").getUnclippedBoundsInRoot()
        val back = onNodeWithTag("miniapp_back_control").getUnclippedBoundsInRoot()
        val settings = onNodeWithTag("miniapp_settings_control").getUnclippedBoundsInRoot()

        val titleCenterY = title.top + (title.bottom - title.top) / 2
        assertTrue(titleCenterY in back.top..back.bottom)
        assertTrue(title.left >= back.right)
        assertTrue(title.right <= settings.left)
    }

    @Test
    fun content_only_mode_animates_the_host_toolbar_out_before_removing_it() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val session = MutableFrameSession()
            setContent {
                RootChildContent(
                    child = running(session),
                    onBack = {},
                    onSettings = {},
                )
            }

            mainClock.advanceTimeByFrame()
            onNodeWithTag("miniapp_back_control").assertIsDisplayed()
            onNodeWithTag("miniapp_settings_control").assertIsDisplayed()

            runOnIdle { session.frameMode.value = MiniAppFrameMode.ContentOnly }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("miniapp_back_control").assertExists()

            mainClock.advanceTimeBy(1_000)
            onNodeWithTag("miniapp_back_control").assertDoesNotExist()
            onNodeWithTag("miniapp_settings_control").assertDoesNotExist()
        }

    @Test
    fun plugin_local_theme_is_confined_to_the_viewport() = runComposeUiTest {
        var pluginPrimary: Color? = null
        var hostTopPrimary: Color? = null
        var hostBottomPrimary: Color? = null
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color.Blue)) {
                MiniAppFrame(
                    onBack = {},
                    onSettings = {},
                    topBar = {
                        val primary = MaterialTheme.colorScheme.primary
                        SideEffect { hostTopPrimary = primary }
                    },
                    bottomBar = {
                        val primary = MaterialTheme.colorScheme.primary
                        SideEffect { hostBottomPrimary = primary }
                    },
                ) { viewport ->
                    MaterialTheme(colorScheme = lightColorScheme(primary = Color.Red)) {
                        val primary = MaterialTheme.colorScheme.primary
                        SideEffect { pluginPrimary = primary }
                        Box(viewport)
                    }
                }
            }
        }

        waitForIdle()
        assertEquals(Color.Red, pluginPrimary)
        assertEquals(Color.Blue, hostTopPrimary)
        assertEquals(Color.Blue, hostBottomPrimary)
    }

    @Test
    fun eligible_empty_banner_slot_keeps_reserved_height() = runComposeUiTest {
        setContent {
            MiniAppFrame(
                onBack = {},
                onSettings = {},
                bottomBar = {},
            ) { viewport ->
                Box(viewport)
            }
        }

        assertEquals(
            50.dp,
            onNodeWithTag("miniapp_banner_container")
                .getUnclippedBoundsInRoot()
                .height,
        )
    }

    private fun running(session: MiniAppSession): RootComponent.Child.RunningMiniApp =
        RootComponent.Child.RunningMiniApp(
            id = MiniAppId("game.test"),
            state = RootComponent.MiniAppState.Content(session),
        )

    private object FakeCatalogComponent : CatalogComponent {
        override val model = MutableValue(CatalogComponent.Model(emptyList()))
        override fun onPlayClicked(id: MiniAppId) = Unit
    }

    private class TaggingSession : MiniAppSession {
        @Composable
        override fun Content(modifier: Modifier) {
            Box(modifier.testTag("session_viewport"))
        }
    }

    private class TopBarSession : MiniAppSession {
        @Composable
        override fun TopBarContent() {
            Box(Modifier.size(24.dp).testTag("session_top_bar"))
        }

        @Composable
        override fun Content(modifier: Modifier) {
            Box(modifier)
        }
    }

    private class BackgroundSession : MiniAppSession {
        @Composable
        override fun Background(modifier: Modifier) {
            Box(modifier.testTag("session_background"))
        }

        @Composable
        override fun Content(modifier: Modifier) {
            Box(modifier)
        }
    }

    private class MutableFrameSession : MiniAppSession {
        override val frameMode = MutableValue(MiniAppFrameMode.Standard)

        @Composable
        override fun Content(modifier: Modifier) {
            Box(modifier)
        }
    }
}
