package ge.yet.game.feature.catalog.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val CatalogExpandedWidth = 840.dp
internal val CatalogMaxContentWidth = 1200.dp

internal fun catalogColumnCount(width: Dp): Int =
    if (width >= CatalogExpandedWidth) 2 else 1

internal fun catalogOuterPadding(width: Dp): Dp =
    if (width >= CatalogExpandedWidth) 24.dp else 16.dp

internal fun catalogContentWidth(width: Dp): Dp =
    minOf(width, CatalogMaxContentWidth)
