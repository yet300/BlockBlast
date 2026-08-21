package ge.yet.game.screen.miniapp

import kotlin.test.Test
import kotlin.test.assertEquals

class MiniAppFrameLayoutPolicyTest {

    @Test
    fun eligible_banner_reserves_stable_space_outside_viewport() {
        val result = miniAppFrameLayout(
            totalHeightDp = 800,
            safeTopDp = 24,
            safeBottomDp = 16,
            showBanner = true,
        )

        assertEquals(64, result.topBarHeightDp)
        assertEquals(50, result.bannerHeightDp)
        assertEquals(646, result.viewportHeightDp)
    }

    @Test
    fun ineligible_banner_removes_the_container_completely() {
        val result = miniAppFrameLayout(
            totalHeightDp = 800,
            safeTopDp = 24,
            safeBottomDp = 16,
            showBanner = false,
        )

        assertEquals(0, result.bannerHeightDp)
        assertEquals(696, result.viewportHeightDp)
    }

    @Test
    fun eligible_empty_banner_slot_keeps_reserved_height() {
        val loadedBanner = miniAppFrameLayout(800, 24, 16, showBanner = true)
        val emptyBanner = miniAppFrameLayout(800, 24, 16, showBanner = true)

        assertEquals(50, loadedBanner.bannerHeightDp)
        assertEquals(loadedBanner.viewportHeightDp, emptyBanner.viewportHeightDp)
    }
}
