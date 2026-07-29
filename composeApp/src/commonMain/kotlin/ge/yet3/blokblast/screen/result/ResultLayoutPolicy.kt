package ge.yet3.blokblast.screen.result

import kotlin.math.max
import kotlin.math.min

internal data class ResultLayoutPolicy(
    val isCompact: Boolean,
    val isUltraCompact: Boolean,
    val horizontalPaddingDp: Float,
    val verticalPaddingDp: Float,
    val sectionSpacingDp: Float,
    val titleSpacingDp: Float,
    val cardHorizontalPaddingDp: Float,
    val cardVerticalPaddingDp: Float,
    val scoreSpacingDp: Float,
    val actionsSpacingDp: Float,
    val buttonHeightDp: Float,
    /**
     * Conservative guardrail for the title and complete actions pane. This is
     * intentionally not presented as an exact Compose measurement.
     */
    val fixedContentGuardrailHeightDp: Float,
)

internal data class ResultLayoutBudget(
    val policy: ResultLayoutPolicy,
    val boardSizeDp: Float,
    val contentHeightDp: Float,
    val fixedContentFits: Boolean,
)

internal fun resultLayoutBudget(
    widthDp: Float,
    heightDp: Float,
): ResultLayoutBudget {
    val policy = resultLayoutPolicy(widthDp = widthDp, heightDp = heightDp)
    val isLandscape = widthDp > heightDp
    val boardSizeDp = if (isLandscape) {
        val availablePaneWidthDp = (
            widthDp -
                policy.horizontalPaddingDp * 2f -
                policy.sectionSpacingDp
            ).coerceAtLeast(0f) / 2f
        val availableBoardHeightDp =
            (heightDp - policy.verticalPaddingDp * 2f).coerceAtLeast(0f)
        min(MAX_BOARD_SIZE_DP, min(availablePaneWidthDp, availableBoardHeightDp))
    } else {
        val availableWidthDp = (widthDp - policy.horizontalPaddingDp * 2f).coerceAtLeast(0f)
        val availableBoardHeightDp =
            (heightDp - policy.fixedContentGuardrailHeightDp).coerceAtLeast(0f)
        min(MAX_BOARD_SIZE_DP, min(availableWidthDp, availableBoardHeightDp))
    }
    val contentHeightDp = if (isLandscape) {
        max(
            policy.fixedContentGuardrailHeightDp,
            boardSizeDp + policy.verticalPaddingDp * 2f,
        )
    } else {
        policy.fixedContentGuardrailHeightDp + boardSizeDp
    }

    return ResultLayoutBudget(
        policy = policy,
        boardSizeDp = boardSizeDp,
        contentHeightDp = contentHeightDp,
        fixedContentFits = policy.fixedContentGuardrailHeightDp <= heightDp,
    )
}

internal fun resultLayoutPolicy(
    widthDp: Float,
    heightDp: Float,
): ResultLayoutPolicy =
    when {
        widthDp > heightDp && heightDp < ULTRA_COMPACT_LANDSCAPE_HEIGHT_DP ->
            UltraCompactResultLayoutPolicy
        heightDp < COMPACT_HEIGHT_THRESHOLD_DP || widthDp > heightDp ->
            CompactResultLayoutPolicy
        else ->
            RegularResultLayoutPolicy
    }

private val UltraCompactResultLayoutPolicy = ResultLayoutPolicy(
    isCompact = true,
    isUltraCompact = true,
    horizontalPaddingDp = 8f,
    verticalPaddingDp = 4f,
    sectionSpacingDp = 4f,
    titleSpacingDp = 0f,
    cardHorizontalPaddingDp = 12f,
    cardVerticalPaddingDp = 6f,
    scoreSpacingDp = 0f,
    actionsSpacingDp = 4f,
    buttonHeightDp = 44f,
    fixedContentGuardrailHeightDp = 264f,
)

private val CompactResultLayoutPolicy = ResultLayoutPolicy(
    isCompact = true,
    isUltraCompact = false,
    horizontalPaddingDp = 12f,
    verticalPaddingDp = 8f,
    sectionSpacingDp = 8f,
    titleSpacingDp = 2f,
    cardHorizontalPaddingDp = 16f,
    cardVerticalPaddingDp = 12f,
    scoreSpacingDp = 4f,
    actionsSpacingDp = 8f,
    buttonHeightDp = 48f,
    fixedContentGuardrailHeightDp = 306f,
)

private val RegularResultLayoutPolicy = ResultLayoutPolicy(
    isCompact = false,
    isUltraCompact = false,
    horizontalPaddingDp = 24f,
    verticalPaddingDp = 24f,
    sectionSpacingDp = 24f,
    titleSpacingDp = 4f,
    cardHorizontalPaddingDp = 24f,
    cardVerticalPaddingDp = 24f,
    scoreSpacingDp = 8f,
    actionsSpacingDp = 12f,
    buttonHeightDp = 56f,
    fixedContentGuardrailHeightDp = 452f,
)

private const val ULTRA_COMPACT_LANDSCAPE_HEIGHT_DP = 360f
private const val COMPACT_HEIGHT_THRESHOLD_DP = 720f
private const val MAX_BOARD_SIZE_DP = 420f
