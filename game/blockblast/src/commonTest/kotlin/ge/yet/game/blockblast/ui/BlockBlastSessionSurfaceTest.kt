package ge.yet.game.blockblast.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BlockBlastSessionSurfaceTest {
    @Test
    fun session_background_fills_the_host_owned_layer() = runComposeUiTest {
        setContent {
            LogicaTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 480.dp)
                        .testTag("blockblast_session_host"),
                ) {
                    BlockBlastSessionBackground(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        onAllNodesWithTag("blockblast_ambient_background").assertCountEquals(1)
        onNodeWithTag("blockblast_ambient_background").assertIsDisplayed()
        assertEquals(
            onNodeWithTag("blockblast_session_host").getUnclippedBoundsInRoot(),
            onNodeWithTag("blockblast_ambient_background").getUnclippedBoundsInRoot(),
        )
    }
}
