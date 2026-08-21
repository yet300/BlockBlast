package ge.yet.game.feature.catalog.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class CatalogContentPadding(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp,
)

internal fun catalogContentPadding(
    contentPadding: Dp,
    safeTop: Dp,
    safeBottom: Dp,
    safeStart: Dp = 0.dp,
    safeEnd: Dp = 0.dp,
): CatalogContentPadding = CatalogContentPadding(
    start = contentPadding + safeStart,
    top = contentPadding + safeTop,
    end = contentPadding + safeEnd,
    bottom = contentPadding + safeBottom,
)
