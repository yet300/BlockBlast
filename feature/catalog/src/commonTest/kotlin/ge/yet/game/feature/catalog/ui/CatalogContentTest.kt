package ge.yet.game.feature.catalog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.catalog.generated.resources.Res
import ge.yet.game.feature.catalog.generated.resources.app_name
import ge.yet.game.feature.catalog.generated.resources.catalog_empty_title
import ge.yet.game.feature.catalog.generated.resources.catalog_placeholder
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CatalogContentTest {
    @Test
    fun list_item_exposes_only_the_direct_play_action() = runComposeUiTest {
        val component = FakeCatalogComponent()
        setContent { TestCatalog(component) }

        onNodeWithTag("catalog_title").assertIsDisplayed()
        onNodeWithTag("catalog_ambient_background").assertIsDisplayed()
        assertEquals(
            onNodeWithTag("catalog_content").getUnclippedBoundsInRoot(),
            onNodeWithTag("catalog_ambient_background").getUnclippedBoundsInRoot(),
        )
        onNodeWithTag("catalog_card_game.blockblast").assertHasNoClickAction()
        onNodeWithTag("catalog_play_game.blockblast").performClick()
        assertEquals(listOf(component.manifest.id), component.played)
    }

    @Test
    fun long_press_does_not_open_a_context_menu() = runComposeUiTest {
        val component = FakeCatalogComponent()
        setContent { TestCatalog(component) }

        onNodeWithTag("catalog_card_game.blockblast")
            .performTouchInput { longClick() }

        onNodeWithTag("catalog_menu_play_game.blockblast").assertDoesNotExist()
        onNodeWithTag("catalog_menu_details_game.blockblast").assertDoesNotExist()
        onNodeWithTag("catalog_details_dialog").assertDoesNotExist()
    }

    @Test
    fun icon_and_play_action_are_vertically_centered() = runComposeUiTest {
        val component = FakeCatalogComponent()
        setContent { TestCatalog(component) }

        val icon = onNodeWithTag(
            testTag = "catalog_icon_game.blockblast",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val play = onNodeWithTag("catalog_play_game.blockblast").getUnclippedBoundsInRoot()
        val iconCenterY = icon.top + (icon.bottom - icon.top) / 2
        val playCenterY = play.top + (play.bottom - play.top) / 2

        assertEquals(iconCenterY, playCenterY)
    }

    @Test
    fun expanded_catalog_places_two_list_items_in_the_first_row() = runComposeUiTest {
        val component = FakeCatalogComponent(count = 3)
        setContent {
            Box(Modifier.size(width = 1000.dp, height = 800.dp)) {
                TestCatalog(component)
            }
        }

        val first = onNodeWithTag("catalog_card_game.test0").getUnclippedBoundsInRoot()
        val second = onNodeWithTag("catalog_card_game.test1").getUnclippedBoundsInRoot()
        val third = onNodeWithTag("catalog_card_game.test2").getUnclippedBoundsInRoot()

        assertEquals(first.top, second.top)
        assertTrue(second.left > first.left)
        assertTrue(third.top > first.top)
    }

    @Composable
    private fun TestCatalog(component: CatalogComponent) {
        LogicaTheme {
            CatalogContent(
                component = component,
                modifier = Modifier,
            )
        }
    }

    private class FakeCatalogComponent(
        count: Int = 1,
    ) : CatalogComponent {
        private val baseManifest = MiniAppManifest(
            id = MiniAppId("game.blockblast"),
            title = Res.string.app_name,
            description = Res.string.catalog_empty_title,
            icon = Res.drawable.catalog_placeholder,
            cover = null,
            category = MiniAppCategoryId("game"),
            sortPriority = 0,
        )
        private val manifests = if (count == 1) {
            listOf(baseManifest)
        } else {
            List(count) { index ->
                baseManifest.copy(id = MiniAppId("game.test$index"))
            }
        }
        val manifest: MiniAppManifest
            get() = manifests.first()
        val played = mutableListOf<MiniAppId>()
        override val model = MutableValue(CatalogComponent.Model(manifests))

        override fun onPlayClicked(id: MiniAppId) {
            played += id
        }


    }
}
