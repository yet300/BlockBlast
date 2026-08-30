package ge.yet.game.fruitmerge.ui

import androidx.compose.ui.graphics.Color
import ge.yet.game.fruitmerge.engine.FruitLevel

internal enum class FruitSilhouette { BERRY, TWIN, HEART, EGG, FLAT, LOBED, TEARDROP, SEAMED, CROWNED, STRIPED }
internal enum class FruitDetail { STAR, STEM_PAIR, SEEDS, BLOOM, SEGMENTS, LEAF, LONG_LEAF, SEAM, DIAMONDS, RIND }
internal enum class FruitFace { SHY, BRIGHT, CHEEKY, SLEEPY, SUNNY, PROUD, CURIOUS, GENTLE, BOLD, SERENE }

internal data class FruitVisualSpec(
    val silhouette: FruitSilhouette,
    val detail: FruitDetail,
    val face: FruitFace,
    val base: Color,
    val highlight: Color,
    val blush: Color,
) {
    val identityKey: String = "${silhouette.name}:${detail.name}:${face.name}"
}

internal fun fruitVisualSpec(level: FruitLevel): FruitVisualSpec = FruitVisualSpecs[level.ordinal]

private val FruitVisualSpecs = listOf(
    FruitVisualSpec(FruitSilhouette.BERRY, FruitDetail.STAR, FruitFace.SHY, Color(0xFF536AC8), Color(0xFFB7C8FF), Color(0xFFF4A7BD)),
    FruitVisualSpec(FruitSilhouette.TWIN, FruitDetail.STEM_PAIR, FruitFace.BRIGHT, Color(0xFFC9344F), Color(0xFFFF9AA8), Color(0xFFFFB3BC)),
    FruitVisualSpec(FruitSilhouette.HEART, FruitDetail.SEEDS, FruitFace.CHEEKY, Color(0xFFF05F66), Color(0xFFFFBEC0), Color(0xFFFFA8B5)),
    FruitVisualSpec(FruitSilhouette.EGG, FruitDetail.BLOOM, FruitFace.SLEEPY, Color(0xFF75519B), Color(0xFFD2B4EA), Color(0xFFE8A8CC)),
    FruitVisualSpec(FruitSilhouette.FLAT, FruitDetail.SEGMENTS, FruitFace.SUNNY, Color(0xFFF29A32), Color(0xFFFFD99C), Color(0xFFFFA68B)),
    FruitVisualSpec(FruitSilhouette.LOBED, FruitDetail.LEAF, FruitFace.PROUD, Color(0xFFD84C42), Color(0xFFFFADA1), Color(0xFFF7959E)),
    FruitVisualSpec(FruitSilhouette.TEARDROP, FruitDetail.LONG_LEAF, FruitFace.CURIOUS, Color(0xFFA8C84F), Color(0xFFE3F29B), Color(0xFFECA69B)),
    FruitVisualSpec(FruitSilhouette.SEAMED, FruitDetail.SEAM, FruitFace.GENTLE, Color(0xFFF39A83), Color(0xFFFFD4C0), Color(0xFFEF9DAA)),
    FruitVisualSpec(FruitSilhouette.CROWNED, FruitDetail.DIAMONDS, FruitFace.BOLD, Color(0xFFE9B83D), Color(0xFFFFE3A1), Color(0xFFE99B86)),
    FruitVisualSpec(FruitSilhouette.STRIPED, FruitDetail.RIND, FruitFace.SERENE, Color(0xFF67AD67), Color(0xFFC2E7A5), Color(0xFFE49B9B)),
)
