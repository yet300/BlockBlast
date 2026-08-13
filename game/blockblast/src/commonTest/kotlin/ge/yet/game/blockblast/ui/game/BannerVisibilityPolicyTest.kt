package ge.yet.game.blockblast.ui.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BannerVisibilityPolicyTest {

    @Test
    fun visible_during_active_game_without_app_overlay() {
        assertTrue(shouldShowBanner(isGameOver = false, isAppOverlayVisible = false))
    }

    @Test
    fun hidden_while_app_overlay_is_visible() {
        assertFalse(shouldShowBanner(isGameOver = false, isAppOverlayVisible = true))
    }

    @Test
    fun hidden_after_game_over() {
        assertFalse(shouldShowBanner(isGameOver = true, isAppOverlayVisible = false))
    }
}
