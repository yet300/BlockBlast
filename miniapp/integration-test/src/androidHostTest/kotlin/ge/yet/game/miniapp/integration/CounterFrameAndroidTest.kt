package ge.yet.game.miniapp.integration

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import ge.yet.game.screen.miniapp.MiniAppFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CounterFrameAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun counter_uses_the_real_frame_without_crossing_host_chrome() {
        lateinit var harness: CounterRootHarness
        composeRule.runOnIdle {
            harness = CounterRootHarness(createAndroidCounterRootGraph())
            harness.resume()
            harness.play()
        }

        try {
            var bannerPresent by mutableStateOf(true)
            var pluginPrimary: Color? = null
            var hostTopPrimary: Color? = null
            var hostBottomPrimary: Color? = null
            var pluginLocal: String? = null
            var hostTopLocal: String? = null
            var hostBottomLocal: String? = null

            composeRule.setContent {
                MaterialTheme(colorScheme = lightColorScheme(primary = Color.Blue)) {
                    CompositionLocalProvider(LocalFrameOwner provides "host") {
                        MiniAppFrame(
                            onBack = {},
                            onSettings = {},
                            topBar = {
                                val primary = MaterialTheme.colorScheme.primary
                                val owner = LocalFrameOwner.current
                                SideEffect {
                                    hostTopPrimary = primary
                                    hostTopLocal = owner
                                }
                                Box(Modifier.testTag("counter_host_top"))
                            },
                            bottomBar = if (bannerPresent) {
                                {
                                    val primary = MaterialTheme.colorScheme.primary
                                    val owner = LocalFrameOwner.current
                                    SideEffect {
                                        hostBottomPrimary = primary
                                        hostBottomLocal = owner
                                    }
                                }
                            } else {
                                null
                            },
                        ) { viewport ->
                            MaterialTheme(colorScheme = lightColorScheme(primary = Color.Red)) {
                                CompositionLocalProvider(LocalFrameOwner provides "plugin") {
                                    val primary = MaterialTheme.colorScheme.primary
                                    val owner = LocalFrameOwner.current
                                    SideEffect {
                                        pluginPrimary = primary
                                        pluginLocal = owner
                                    }
                                    harness.session().Content(
                                        viewport.testTag("counter_viewport"),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            composeRule.onNodeWithText("Count: 0").assertIsDisplayed()
            composeRule.onNodeWithText("Visibility: ACTIVE").assertIsDisplayed()
            composeRule.onNodeWithText("Increment").performClick()
            composeRule.onNodeWithText("Count: 1").assertIsDisplayed()

            val withBanner = composeRule.onNodeWithTag("counter_viewport")
                .getUnclippedBoundsInRoot()
            val toolbar = composeRule.onNodeWithTag("miniapp_back_control")
                .getUnclippedBoundsInRoot()
            val banner = composeRule.onNodeWithTag("miniapp_banner_container")
                .getUnclippedBoundsInRoot()
            assertTrue(withBanner.top >= toolbar.bottom)
            assertTrue(withBanner.bottom <= banner.top)
            assertEquals(50.dp, banner.height)

            composeRule.runOnIdle { bannerPresent = false }
            composeRule.waitForIdle()

            val withoutBanner = composeRule.onNodeWithTag("counter_viewport")
                .getUnclippedBoundsInRoot()
            composeRule.onNodeWithTag("miniapp_banner_container").assertDoesNotExist()
            assertEquals(50.dp, withoutBanner.height - withBanner.height)
            assertEquals(Color.Red, pluginPrimary)
            assertEquals(Color.Blue, hostTopPrimary)
            assertEquals(Color.Blue, hostBottomPrimary)
            assertEquals("plugin", pluginLocal)
            assertEquals("host", hostTopLocal)
            assertEquals("host", hostBottomLocal)
        } finally {
            composeRule.runOnIdle { harness.destroy() }
        }
    }

    private companion object {
        val LocalFrameOwner = compositionLocalOf { "host" }
    }
}
