package ge.yet.game.fruitmerge.ui

import androidx.compose.ui.graphics.Color
import ge.yet.game.fruitmerge.engine.FruitLevel

internal enum class FruitSilhouette { BERRY, CLUSTER, HEART, CITRUS, FLAT, LOBED, TEARDROP, SEAMED, CROWNED, STRIPED }
internal enum class FruitDetail { STAR, DRUPELETS, SEEDS, WEDGES, SEGMENTS, LEAF, LONG_LEAF, SEAM, DIAMONDS, RIND }
internal enum class FruitFace { SHY, BRIGHT, CHEEKY, SLEEPY, SUNNY, PROUD, CURIOUS, GENTLE, BOLD, SERENE }
internal enum class FruitLightDirection { UPPER_LEFT }

internal val MarketFruitOutline = Color(0xFF4A2E26)

internal data class FruitVisualSpec(
    val silhouette: FruitSilhouette,
    val detail: FruitDetail,
    val face: FruitFace,
    val base: Color,
    val shadow: Color,
    val highlight: Color,
    val outline: Color = MarketFruitOutline,
    val faceInk: Color = Color(0xFF442B23),
    val blush: Color = Color(0xFFFF8A80),
    val accent: Color = highlight,
    val leaf: Color = Color(0xFF4CAF50),
    val leafShadow: Color = Color(0xFF2E7D32),
    val stem: Color = Color(0xFF6D4C41),
    val lightDirection: FruitLightDirection = FruitLightDirection.UPPER_LEFT,
) {
    val identityKey: String = "${silhouette.name}:${detail.name}:${face.name}"
}

internal fun fruitVisualSpec(level: FruitLevel): FruitVisualSpec = FruitVisualSpecs[level.ordinal]

private val FruitVisualSpecs = listOf(
    // 0. BLUEBERRY (Tiny, Sleepy / Chill)
    FruitVisualSpec(
        silhouette = FruitSilhouette.BERRY,
        detail = FruitDetail.STAR,
        face = FruitFace.SLEEPY,
        base = Color(0xFF4361EE),
        shadow = Color(0xFF283B99),
        highlight = Color(0xFF8DA4FF),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF242E64),
        blush = Color(0xFFFFA8BA),
        accent = Color(0xFFA5B8FF),
        leaf = Color(0xFF384A9E),
        leafShadow = Color(0xFF253372),
    ),
    // 1. RASPBERRY (Small, Mischievous / Playful)
    FruitVisualSpec(
        silhouette = FruitSilhouette.CLUSTER,
        detail = FruitDetail.DRUPELETS,
        face = FruitFace.CHEEKY,
        base = Color(0xFFE6396B),
        shadow = Color(0xFFA81C48),
        highlight = Color(0xFFFF7DA7),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF4A1024),
        blush = Color(0xFFFF80AB),
        accent = Color(0xFFFFB2CC),
        leaf = Color(0xFF589B48),
        leafShadow = Color(0xFF38682C),
    ),
    // 2. STRAWBERRY (Small-Med, Cheerful / Energetic)
    FruitVisualSpec(
        silhouette = FruitSilhouette.HEART,
        detail = FruitDetail.SEEDS,
        face = FruitFace.BRIGHT,
        base = Color(0xFFF03E48),
        shadow = Color(0xFFB51C26),
        highlight = Color(0xFFFF858C),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF4D1217),
        blush = Color(0xFFFF8080),
        accent = Color(0xFFFFEB85),
        leaf = Color(0xFF4CAF50),
        leafShadow = Color(0xFF2E7D32),
    ),
    // 3. LIME (Medium, Sunny / Friendly)
    FruitVisualSpec(
        silhouette = FruitSilhouette.CITRUS,
        detail = FruitDetail.WEDGES,
        face = FruitFace.SUNNY,
        base = Color(0xFF72C73B),
        shadow = Color(0xFF428C1A),
        highlight = Color(0xFFAEF578),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF234710),
        blush = Color(0xFFC6FF80),
        accent = Color(0xFF387E15),
        leaf = Color(0xFF5CA332),
        leafShadow = Color(0xFF37681C),
        stem = Color(0xFF5D4037),
    ),
    // 4. MANDARIN (Medium, Cozy / Contented)
    FruitVisualSpec(
        silhouette = FruitSilhouette.FLAT,
        detail = FruitDetail.SEGMENTS,
        face = FruitFace.GENTLE,
        base = Color(0xFFFA8320),
        shadow = Color(0xFFC04F06),
        highlight = Color(0xFFFFB366),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF542508),
        blush = Color(0xFFFF9E80),
        accent = Color(0xFFE05F0A),
        leaf = Color(0xFF388E3C),
        leafShadow = Color(0xFF1B5E20),
        stem = Color(0xFF5D4037),
    ),
    // 5. APPLE (Med-Large, Proud / Crisp)
    FruitVisualSpec(
        silhouette = FruitSilhouette.LOBED,
        detail = FruitDetail.LEAF,
        face = FruitFace.PROUD,
        base = Color(0xFFE53935),
        shadow = Color(0xFFA31515),
        highlight = Color(0xFFFF8A80),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF471111),
        blush = Color(0xFFFF8585),
        accent = Color(0xFFFFB4AB),
        leaf = Color(0xFF43A047),
        leafShadow = Color(0xFF2E7D32),
        stem = Color(0xFF5D4037),
    ),
    // 6. PEAR (Large, Curious / Quirky)
    FruitVisualSpec(
        silhouette = FruitSilhouette.TEARDROP,
        detail = FruitDetail.LONG_LEAF,
        face = FruitFace.CURIOUS,
        base = Color(0xFFB5D33D),
        shadow = Color(0xFF7A9B1A),
        highlight = Color(0xFFE4F783),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF384510),
        blush = Color(0xFFFFD54F),
        accent = Color(0xFF8EA824),
        leaf = Color(0xFF689F38),
        leafShadow = Color(0xFF33691E),
        stem = Color(0xFF6D4C41),
    ),
    // 7. PEACH (Large, Serene / Gentle)
    FruitVisualSpec(
        silhouette = FruitSilhouette.SEAMED,
        detail = FruitDetail.SEAM,
        face = FruitFace.SERENE,
        base = Color(0xFFFF8A80),
        shadow = Color(0xFFD65264),
        highlight = Color(0xFFFFCDD2),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF5C242C),
        blush = Color(0xFFFF5252),
        accent = Color(0xFFC24054),
        leaf = Color(0xFF66BB6A),
        leafShadow = Color(0xFF388E3C),
        stem = Color(0xFF6D4C41),
    ),
    // 8. PINEAPPLE (Very Large, Bold / Royal)
    FruitVisualSpec(
        silhouette = FruitSilhouette.CROWNED,
        detail = FruitDetail.DIAMONDS,
        face = FruitFace.BOLD,
        base = Color(0xFFF9A825),
        shadow = Color(0xFFB86B04),
        highlight = Color(0xFFFFE082),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF4E2C04),
        blush = Color(0xFFFFB74D),
        accent = Color(0xFFC97A0C),
        leaf = Color(0xFF2E7D32),
        leafShadow = Color(0xFF1B5E20),
        stem = Color(0xFF4E2C04),
    ),
    // 9. WATERMELON (Giant, Zen Master / Jolly)
    FruitVisualSpec(
        silhouette = FruitSilhouette.STRIPED,
        detail = FruitDetail.RIND,
        face = FruitFace.SHY,
        base = Color(0xFF43A047),
        shadow = Color(0xFF1B5E20),
        highlight = Color(0xFFA5D6A7),
        outline = MarketFruitOutline,
        faceInk = Color(0xFF103814),
        blush = Color(0xFFFF8A80),
        accent = Color(0xFF154A19),
        leaf = Color(0xFF2E7D32),
        leafShadow = Color(0xFF1B5E20),
        stem = Color(0xFF2E7D32),
    ),
)
