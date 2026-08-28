package ge.yet.game.twentyfortyeight.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun `visible tutorial uses resource copy and exposes skip action`() = runComposeUiTest {
        var skipped = 0
        setContent {
            LogicaTheme(darkTheme = false) {
                TutorialOverlay(
                    visible = true,
                    active = true,
                    policy = MotionPolicy.Reduced,
                    onSkip = { skipped += 1 },
                )
            }
        }

        onNodeWithTag("tutorial").assertIsDisplayed()
        onNodeWithText("Swipe to combine matching tiles.").assertIsDisplayed()
        onNodeWithText("Skip").assertHasClickAction().performClick()
        assertEquals(1, skipped)
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
