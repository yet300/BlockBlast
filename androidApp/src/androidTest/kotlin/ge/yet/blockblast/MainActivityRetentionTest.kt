package ge.yet.blockblast

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.yet.game.feature.root.RootComponent
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityRetentionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun running_session_survives_activity_recreation() {
        val rootBeforeRecreation = compose.activity.rootComponentForTest()

        compose.onNodeWithTag("catalog_play_game.blockblast").performClick()
        compose.onNodeWithTag("miniapp_frame").assertExists()

        compose.activityRule.scenario.recreate()

        assertSame(rootBeforeRecreation, compose.activity.rootComponentForTest())
        compose.onNodeWithTag("miniapp_frame").assertExists()
        compose.onNodeWithTag("catalog_play_game.blockblast").assertDoesNotExist()
    }

    private fun MainActivity.rootComponentForTest(): RootComponent {
        val field = MainActivity::class.java.getDeclaredField("rootComponent")
        field.isAccessible = true
        return field.get(this) as RootComponent
    }
}
