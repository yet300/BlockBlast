package ge.yet.game.fruitmerge.ui

import androidx.compose.ui.graphics.Color
import ge.yet.game.fruitmerge.engine.FruitLevel

internal enum class FruitSilhouette { BERRY, CLUSTER, HEART, CITRUS, FLAT, LOBED, TEARDROP, SEAMED, CROWNED, STRIPED }
internal enum class FruitDetail { STAR, DRUPELETS, SEEDS, WEDGES, SEGMENTS, LEAF, LONG_LEAF, SEAM, DIAMONDS, RIND }
internal enum class FruitFace { SHY, BRIGHT, CHEEKY, SLEEPY, SUNNY, PROUD, CURIOUS, GENTLE, BOLD, SERENE }
internal enum class FruitLightDirection { UPPER_LEFT }

internal val MarketFruitOutline = Color(0xFF55372D)

internal data class FruitVisualSpec(
    val silhouette: FruitSilhouette,
    val detail: FruitDetail,
    val face: FruitFace,
    val base: Color,
    val shadow: Color,
    val highlight: Color,
    val outline: Color = MarketFruitOutline,
    val faceInk: Color = Color(0xFF49372F),
    val lightDirection: FruitLightDirection = FruitLightDirection.UPPER_LEFT,
) {
    val identityKey: String = "${silhouette.name}:${detail.name}:${face.name}"
}

internal fun fruitVisualSpec(level: FruitLevel): FruitVisualSpec = FruitVisualSpecs[level.ordinal]

private val FruitVisualSpecs = listOf(
    FruitVisualSpec(
        FruitSilhouette.BERRY,
        FruitDetail.STAR,
        FruitFace.SHY,
        Color(0xFF4056A8),
        Color(0xFF2D397A),
        Color(0xFF91A6EE),
        faceInk = Color(0xFFFFF1D4),
    ),
    FruitVisualSpec(FruitSilhouette.CLUSTER, FruitDetail.DRUPELETS, FruitFace.BRIGHT, Color(0xFFD63C68), Color(0xFFA92351), Color(0xFFF06B91)),
    FruitVisualSpec(FruitSilhouette.HEART, FruitDetail.SEEDS, FruitFace.CHEEKY, Color(0xFFE94F55), Color(0xFFB92F3B), Color(0xFFFF8580)),
    FruitVisualSpec(FruitSilhouette.CITRUS, FruitDetail.WEDGES, FruitFace.SUNNY, Color(0xFF70B94F), Color(0xFF3D873D), Color(0xFFA8D96C)),
    FruitVisualSpec(FruitSilhouette.FLAT, FruitDetail.SEGMENTS, FruitFace.SUNNY, Color(0xFFF29A32), Color(0xFFC86C25), Color(0xFFFFD99C)),
    FruitVisualSpec(FruitSilhouette.LOBED, FruitDetail.LEAF, FruitFace.PROUD, Color(0xFFD84C42), Color(0xFFA92E31), Color(0xFFFFADA1)),
    FruitVisualSpec(FruitSilhouette.TEARDROP, FruitDetail.LONG_LEAF, FruitFace.CURIOUS, Color(0xFFA8C84F), Color(0xFF718D35), Color(0xFFE3F29B)),
    FruitVisualSpec(FruitSilhouette.SEAMED, FruitDetail.SEAM, FruitFace.GENTLE, Color(0xFFF39A83), Color(0xFFC96F78), Color(0xFFFFD4C0)),
    FruitVisualSpec(FruitSilhouette.CROWNED, FruitDetail.DIAMONDS, FruitFace.BOLD, Color(0xFFE9B83D), Color(0xFF9D7934), Color(0xFFFFE3A1)),
    FruitVisualSpec(FruitSilhouette.STRIPED, FruitDetail.RIND, FruitFace.SERENE, Color(0xFF67AD67), Color(0xFF3D8751), Color(0xFFC2E7A5)),
)
