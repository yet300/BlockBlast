package ge.yet3.blokblast.screen.result

import kotlin.math.min

internal data class ResultLayoutPolicy(
    val isCompact: Boolean,
    val horizontalPaddingDp: Float,
    val verticalPaddingDp: Float,
    val sectionSpacingDp: Float,
    val titleSpacingDp: Float,
    val cardHorizontalPaddingDp: Float,
    val cardVerticalPaddingDp: Float,
    val scoreSpacingDp: Float,
    val actionsSpacingDp: Float,
    val buttonHeightDp: Float,
    val fixedContentHeightDp: Float,
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
    val availableWidthDp = (widthDp - policy.horizontalPaddingDp * 2f).coerceAtLeast(0f)
    val availableBoardHeightDp = (heightDp - policy.fixedContentHeightDp).coerceAtLeast(0f)
    val boardSizeDp = min(MAX_BOARD_SIZE_DP, min(availableWidthDp, availableBoardHeightDp))

    return ResultLayoutBudget(
        policy = policy,
        boardSizeDp = boardSizeDp,
        contentHeightDp = policy.fixedContentHeightDp + boardSizeDp,
        fixedContentFits = policy.fixedContentHeightDp <= heightDp,
    )
}

internal fun resultLayoutPolicy(
    widthDp: Float,
    heightDp: Float,
): ResultLayoutPolicy =
    if (heightDp < COMPACT_HEIGHT_THRESHOLD_DP || widthDp > heightDp) {
        CompactResultLayoutPolicy
    } else {
        RegularResultLayoutPolicy
    }

private val CompactResultLayoutPolicy = ResultLayoutPolicy(
    isCompact = true,
    horizontalPaddingDp = 12f,
    verticalPaddingDp = 8f,
    sectionSpacingDp = 8f,
    titleSpacingDp = 2f,
    cardHorizontalPaddingDp = 16f,
    cardVerticalPaddingDp = 12f,
    scoreSpacingDp = 4f,
    actionsSpacingDp = 8f,
    buttonHeightDp = 48f,
    fixedContentHeightDp = 306f,
)

private val RegularResultLayoutPolicy = ResultLayoutPolicy(
    isCompact = false,
    horizontalPaddingDp = 24f,
    verticalPaddingDp = 24f,
    sectionSpacingDp = 24f,
    titleSpacingDp = 4f,
    cardHorizontalPaddingDp = 24f,
    cardVerticalPaddingDp = 24f,
    scoreSpacingDp = 8f,
    actionsSpacingDp = 12f,
    buttonHeightDp = 56f,
    fixedContentHeightDp = 452f,
)

private const val COMPACT_HEIGHT_THRESHOLD_DP = 720f
private const val MAX_BOARD_SIZE_DP = 420f
