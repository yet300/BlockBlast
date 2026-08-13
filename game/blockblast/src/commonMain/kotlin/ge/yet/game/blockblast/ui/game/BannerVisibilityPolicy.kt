package ge.yet.game.blockblast.ui.game

internal fun shouldShowBanner(
    isGameOver: Boolean,
    isAppOverlayVisible: Boolean,
): Boolean = !isGameOver && !isAppOverlayVisible
