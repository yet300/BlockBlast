package ge.yet.game.twentyfortyeight.ui.overlay

import ge.yet.game.twentyfortyeight.ui.motion.MotionPolicy

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TutorialOverlayTest {
    @Test
    fun `tutorial motion runs only while visible active and normal`() {
        assertEquals(
            TutorialMotionMode.Animated,
            tutorialMotionMode(visible = true, active = true, policy = MotionPolicy.Normal),
        )
        assertEquals(
            TutorialMotionMode.Static,
            tutorialMotionMode(visible = true, active = false, policy = MotionPolicy.Normal),
        )
        assertEquals(
            TutorialMotionMode.Static,
            tutorialMotionMode(visible = true, active = true, policy = MotionPolicy.Reduced),
        )
        assertEquals(
            TutorialMotionMode.Hidden,
            tutorialMotionMode(visible = false, active = true, policy = MotionPolicy.Normal),
        )
    }

    @Test
    fun `visible tutorial is a wordless gesture overlay`() = runComposeUiTest {
        setContent {
            LogicaTheme(darkTheme = false) {
                TutorialOverlay(
                    visible = true,
                    active = true,
                    policy = MotionPolicy.Reduced,
                    onSkip = {},
                )
            }
        }

        onNodeWithTag("tutorial").assertIsDisplayed()
        onNodeWithTag("tutorial_illustration").assertIsDisplayed()
        onNodeWithText("Swipe to combine matching tiles.").assertDoesNotExist()
        onNodeWithText("Skip").assertDoesNotExist()
        onNodeWithTag("tutorial").assertHasNoClickAction()
    }

    @Test
    fun `hidden tutorial contributes no semantics`() = runComposeUiTest {
        setContent {
            TutorialOverlay(
                visible = false,
                active = true,
                policy = MotionPolicy.Normal,
                onSkip = {},
            )
        }

        onNodeWithTag("tutorial").assertDoesNotExist()
    }
}
