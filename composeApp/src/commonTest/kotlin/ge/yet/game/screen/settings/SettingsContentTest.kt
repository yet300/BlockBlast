package ge.yet.game.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.feature.settings.reset.ResetGameDataComponent
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.screen.settings.content.ResetGameDataContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsContentTest {
    @Test
    fun confirmation_distinguishes_game_data_from_preserved_app_preferences() = runComposeUiTest {
        val component = FakeResetComponent()
        setContent { ResetGameDataContent(component) }

        onNodeWithText(
            "This permanently deletes saved games, scores, tutorials, and other progress from every installed game.",
        ).assertIsDisplayed()
        onNodeWithText(
            "Your app preferences, privacy choices, advertising consent, and review settings will not be deleted.",
        ).assertIsDisplayed()
        onNodeWithTag("clear_game_data_confirm").performClick()
        assertEquals(1, component.confirmClicks)
    }

    @Test
    fun progress_hides_destructive_action_and_success_exposes_done() = runComposeUiTest {
        val component = FakeResetComponent()
        setContent { ResetGameDataContent(component) }

        component.set(ResetGameDataComponent.Status.Clearing)
        onNodeWithTag("clear_game_data_progress").assertIsDisplayed()
        onNodeWithTag("clear_game_data_confirm").assertDoesNotExist()

        component.set(ResetGameDataComponent.Status.Success)
        onNodeWithTag("clear_game_data_done").performClick()
        assertEquals(1, component.backClicks)
    }

    @Test
    fun partial_failure_lists_stable_ids_and_exposes_retry() = runComposeUiTest {
        val component = FakeResetComponent()
        setContent { ResetGameDataContent(component) }
        component.set(
            ResetGameDataComponent.Status.PartialFailure(
                setOf(MiniAppId("game.zeta"), MiniAppId("game.alpha")),
            ),
        )

        onNodeWithText("game.alpha\ngame.zeta").assertIsDisplayed()
        onNodeWithTag("clear_game_data_retry").performClick()
        assertEquals(1, component.retryClicks)
    }

    private class FakeResetComponent : ResetGameDataComponent {
        private val mutableModel = MutableValue(
            ResetGameDataComponent.Model(ResetGameDataComponent.Status.Confirming),
        )
        override val model: Value<ResetGameDataComponent.Model> = mutableModel
        var confirmClicks = 0
        var retryClicks = 0
        var backClicks = 0

        fun set(status: ResetGameDataComponent.Status) {
            mutableModel.value = ResetGameDataComponent.Model(status)
        }

        override fun onConfirmClicked() {
            confirmClicks += 1
        }

        override fun onRetryClicked() {
            retryClicks += 1
        }

        override fun onBackClicked() {
            backClicks += 1
        }
    }
}
