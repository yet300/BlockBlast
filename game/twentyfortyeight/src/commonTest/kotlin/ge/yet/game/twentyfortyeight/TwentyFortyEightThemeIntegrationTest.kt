package ge.yet.game.twentyfortyeight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.twentyfortyeight.component.OverlayComponent
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class TwentyFortyEightThemeIntegrationTest {
    @Test
    fun `session Background fills the host light and dark backgrounds`() = runComposeUiTest {
        val session = TwentyFortyEightSession(FakeSessionComponent())

        val light = renderBackground(session, darkTheme = false)
        val dark = renderBackground(session, darkTheme = true)

        assertEquals(light.expected, light.actual)
        assertEquals(dark.expected, dark.actual)
        assertNotEquals(light.actual, dark.actual)
    }

    @Test
    fun `session Content inherits the host light and dark backgrounds`() = runComposeUiTest {
        val session = TwentyFortyEightSession(FakeSessionComponent())

        val light = renderContent(session, darkTheme = false)
        val dark = renderContent(session, darkTheme = true)

        assertEquals(light.expected, light.actual)
        assertEquals(dark.expected, dark.actual)
        assertNotEquals(light.actual, dark.actual)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.renderBackground(
        session: TwentyFortyEightSession,
        darkTheme: Boolean,
    ): RenderedColors {
        var expected = Color.Unspecified
        setContent {
            LogicaTheme(darkTheme = darkTheme) {
                expected = MaterialTheme.colorScheme.background
                session.Background(
                    Modifier
                        .size(32.dp)
                        .testTag(BackgroundTag),
                )
            }
        }
        val actual = onNodeWithTag(BackgroundTag).captureToImage().toPixelMap()[0, 0]
        return RenderedColors(expected, actual)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.renderContent(
        session: TwentyFortyEightSession,
        darkTheme: Boolean,
    ): RenderedColors {
        var expected = Color.Unspecified
        setContent {
            LogicaTheme(darkTheme = darkTheme) {
                expected = MaterialTheme.colorScheme.background
                Box(Modifier.size(400.dp, 700.dp)) {
                    session.Content(
                        Modifier
                            .fillMaxSize()
                            .testTag(ContentTag),
                    )
                }
            }
        }
        val actual = onNodeWithTag(ContentTag).captureToImage().toPixelMap()[0, 0]
        return RenderedColors(expected, actual)
    }

    private data class RenderedColors(val expected: Color, val actual: Color)

    private class FakeSessionComponent : TwentyFortyEightSessionComponent {
        private val playing = FakePlayingComponent()
        override val stack: Value<ChildStack<*, TwentyFortyEightSessionComponent.Child>> = MutableValue(
            ChildStack(Unit, TwentyFortyEightSessionComponent.Child.Playing(playing)),
        )
        override val frameMode: Value<MiniAppFrameMode> = MutableValue(MiniAppFrameMode.Standard)
        override val effect: Value<TwentyFortyEightSessionComponent.EffectState> =
            MutableValue(TwentyFortyEightSessionComponent.EffectState())

        override fun onEffectConsumed(effectId: Long) = Unit
        override fun handleBack(): Boolean = false
    }

    private class FakePlayingComponent : PlayingComponent {
        override val model: Value<PlayingComponent.Model> = MutableValue(
            PlayingComponent.Model(
                board = null,
                transition = null,
                score = 0L,
                bestScore = 0L,
                gesturesEnabled = true,
                undoEnabled = false,
                tutorialVisible = false,
                overlay = null,
                persistenceStatus = PlayingComponent.PersistenceStatus.Clean,
            ),
        )
        override val overlay: Value<ChildSlot<*, OverlayComponent>> =
            MutableValue(ChildSlot<Unit, OverlayComponent>())

        override fun onMove(direction: Direction) = Unit
        override fun onUndoRequested() = Unit
        override fun onRestartRequested() = Unit
        override fun onContinueAfterVictory() = Unit
        override fun onTutorialSkipped() = Unit
        override fun onAnimationCompleted(transitionId: Long) = Unit
        override fun handleBack(): Boolean = false
    }

    private companion object {
        const val BackgroundTag = "session_background"
        const val ContentTag = "session_content"
    }
}
