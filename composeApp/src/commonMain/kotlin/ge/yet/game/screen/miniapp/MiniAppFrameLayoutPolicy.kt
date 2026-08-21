package ge.yet.game.screen.miniapp

internal const val MINI_APP_TOP_BAR_HEIGHT_DP = 64
internal const val MINI_APP_BANNER_HEIGHT_DP = 50

internal data class MiniAppFrameLayout(
    val topBarHeightDp: Int,
    val bannerHeightDp: Int,
    val viewportHeightDp: Int,
)

internal fun miniAppFrameLayout(
    totalHeightDp: Int,
    safeTopDp: Int,
    safeBottomDp: Int,
    showBanner: Boolean,
): MiniAppFrameLayout {
    val bannerHeightDp = if (showBanner) MINI_APP_BANNER_HEIGHT_DP else 0
    return MiniAppFrameLayout(
        topBarHeightDp = MINI_APP_TOP_BAR_HEIGHT_DP,
        bannerHeightDp = bannerHeightDp,
        viewportHeightDp = (
            totalHeightDp - safeTopDp - safeBottomDp -
                MINI_APP_TOP_BAR_HEIGHT_DP - bannerHeightDp
            ).coerceAtLeast(0),
    )
}
