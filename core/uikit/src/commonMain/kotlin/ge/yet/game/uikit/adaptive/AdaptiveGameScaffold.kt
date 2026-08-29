package ge.yet.game.uikit.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

private const val COMPACT_AND_MEDIUM_PRIMARY_MAX_DP = 520
private const val EXPANDED_PRIMARY_MAX_DP = 560
private const val CONTENT_MAX_DP = 1_200
private const val SUPPORTING_MIN_DP = 280
private const val SUPPORTING_MAX_DP = 360
private const val COMPACT_HEIGHT_PRIMARY_MIN_DP = 240
private const val HORIZONTAL_PADDING_DP = 32
private const val COMPACT_HEIGHT_SPACING_DP = 16
private const val COMPACT_HEIGHT_TWO_PANE_MIN_WIDTH_DP =
    HORIZONTAL_PADDING_DP + COMPACT_HEIGHT_SPACING_DP +
        SUPPORTING_MIN_DP + COMPACT_HEIGHT_PRIMARY_MIN_DP

internal enum class AdaptiveGameArrangement {
    Vertical,
    TwoPane,
    CompactHeightTwoPane,
}

internal data class AdaptiveGameMetrics(
    val arrangement: AdaptiveGameArrangement,
    val primaryMaxDp: Int,
    val contentMaxDp: Int,
    val supportingMinDp: Int,
    val supportingMaxDp: Int,
    val windowSizeClass: WindowSizeClass,
)

internal fun adaptiveGameMetrics(
    availableWidthDp: Int,
    availableHeightDp: Int,
): AdaptiveGameMetrics = adaptiveGameMetrics(
    availableWidthDp = availableWidthDp.toFloat(),
    availableHeightDp = availableHeightDp.toFloat(),
)

internal fun adaptiveGameMetrics(
    availableWidthDp: Float,
    availableHeightDp: Float,
): AdaptiveGameMetrics {
    require(
        availableWidthDp.isFinite() && availableHeightDp.isFinite() &&
            availableWidthDp >= 0f && availableHeightDp >= 0f,
    ) {
        "Adaptive game viewport dimensions must be finite and non-negative"
    }
    val windowSizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
        widthDp = availableWidthDp,
        heightDp = availableHeightDp,
    )
    val expandedWidth = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    )
    val compactHeight = !windowSizeClass.isHeightAtLeastBreakpoint(
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
    )
    return AdaptiveGameMetrics(
        arrangement = when {
            compactHeight && availableWidthDp >= COMPACT_HEIGHT_TWO_PANE_MIN_WIDTH_DP -> {
                AdaptiveGameArrangement.CompactHeightTwoPane
            }
            expandedWidth -> AdaptiveGameArrangement.TwoPane
            else -> AdaptiveGameArrangement.Vertical
        },
        primaryMaxDp = if (expandedWidth) {
            EXPANDED_PRIMARY_MAX_DP
        } else {
            COMPACT_AND_MEDIUM_PRIMARY_MAX_DP
        },
        contentMaxDp = CONTENT_MAX_DP,
        supportingMinDp = SUPPORTING_MIN_DP,
        supportingMaxDp = SUPPORTING_MAX_DP,
        windowSizeClass = windowSizeClass,
    )
}

@Composable
fun AdaptiveGameScaffold(
    modifier: Modifier = Modifier,
    supportingPaneModifier: Modifier = Modifier,
    primary: @Composable BoxScope.() -> Unit,
    supporting: @Composable ColumnScope.() -> Unit,
) {
    val latestPrimary = rememberUpdatedState(primary)
    val latestSupporting = rememberUpdatedState(supporting)
    val latestSupportingPaneModifier = rememberUpdatedState(supportingPaneModifier)
    val supportingScrollState = rememberScrollState()
    val movablePrimary = remember {
        movableContentOf<Modifier> { paneModifier ->
            Box(
                modifier = paneModifier,
                contentAlignment = Alignment.Center,
                content = latestPrimary.value,
            )
        }
    }
    val movableSupporting = remember {
        movableContentOf<Modifier> { paneModifier ->
            Column(
                modifier = latestSupportingPaneModifier.value
                    .then(paneModifier)
                    .verticalScroll(supportingScrollState),
                content = latestSupporting.value,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val metrics = adaptiveGameMetrics(
            availableWidthDp = maxWidth.value,
            availableHeightDp = maxHeight.value,
        )
        when (metrics.arrangement) {
            AdaptiveGameArrangement.Vertical -> VerticalGameLayout(
                metrics = metrics,
                primary = movablePrimary,
                supporting = movableSupporting,
            )

            AdaptiveGameArrangement.TwoPane -> TwoPaneGameLayout(
                metrics = metrics,
                horizontalSpacingDp = 24,
                primary = movablePrimary,
                supporting = movableSupporting,
            )

            AdaptiveGameArrangement.CompactHeightTwoPane -> TwoPaneGameLayout(
                metrics = metrics,
                horizontalSpacingDp = 16,
                primary = movablePrimary,
                supporting = movableSupporting,
            )
        }
    }
}

@Composable
private fun VerticalGameLayout(
    metrics: AdaptiveGameMetrics,
    primary: @Composable (Modifier) -> Unit,
    supporting: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = metrics.contentMaxDp.dp)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            primary(
                Modifier
                    .sizeIn(
                        maxWidth = metrics.primaryMaxDp.dp,
                        maxHeight = metrics.primaryMaxDp.dp,
                    )
                    .aspectRatio(1f)
                    .fillMaxSize(),
            )
        }
        supporting(
            Modifier
                .weight(1f)
                .widthIn(max = metrics.primaryMaxDp.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun TwoPaneGameLayout(
    metrics: AdaptiveGameMetrics,
    horizontalSpacingDp: Int,
    primary: @Composable (Modifier) -> Unit,
    supporting: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(max = metrics.contentMaxDp.dp)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            primary(
                Modifier
                    .sizeIn(
                        maxWidth = metrics.primaryMaxDp.dp,
                        maxHeight = metrics.primaryMaxDp.dp,
                    )
                    .aspectRatio(1f)
                    .fillMaxSize(),
            )
        }
        supporting(
            (if (metrics.arrangement == AdaptiveGameArrangement.CompactHeightTwoPane) {
                Modifier.width(metrics.supportingMinDp.dp)
            } else {
                Modifier.widthIn(
                    min = metrics.supportingMinDp.dp,
                    max = metrics.supportingMaxDp.dp,
                )
            })
                .fillMaxHeight(),
        )
    }
}
