package ge.yet3.blokblast.theme

import androidx.compose.ui.graphics.Color
// ── Background & Surface ──────────────────────────────────────────────────
val Parchment = Color(0xFFF5F4ED) // Primary page background (warm cream)
val Ivory = Color(0xFFFAF9F5)     // Card surfaces, elevated containers
val PureWhite = Color(0xFFFFFFFF)
val WarmSand = Color(0xFFE8E6DC)  // Button backgrounds, interactive surfaces
val DarkSurface = Color(0xFF30302E)
val AnthropicNearBlack = Color(0xFF141413) // Primary text, dark theme background

// ── Brand & Accents ───────────────────────────────────────────────────────
val TerracottaBrand = Color(0xFFC96442) // Core brand CTA
val CoralAccent = Color(0xFFD97757)     // Text accents
val ErrorCrimson = Color(0xFFB53333)    // Error states
val FocusBlue = Color(0xFF3898EC)       // Focus rings (only cool color)

// ── Neutrals & Text ───────────────────────────────────────────────────────
val CharcoalWarm = Color(0xFF4D4C48)    // Button text on light
val OliveGray = Color(0xFF5E5D59)       // Secondary body text
val StoneGray = Color(0xFF87867F)       // Tertiary text, metadata
val DarkWarm = Color(0xFF3D3D3A)        // Emphasized secondary
val WarmSilver = Color(0xFFB0AEA5)      // Text on dark surfaces

// ── Borders & Shadows (Rings) ─────────────────────────────────────────────
val BorderCream = Color(0xFFF0EEE6)     // Standard light border
val BorderWarm = Color(0xFFE8E6DC)      // Section dividers
val RingWarm = Color(0xFFD1CFC5)        // Button hover/focus ring
val RingDeep = Color(0xFFC2C0B6)        // Active/pressed ring

// ── Organic Piece Colors for the Game (1..6) ──────────────────────────────
val PieceColors = listOf(
    TerracottaBrand,       // 1 · Brand Orange-Brown
    Color(0xFF6E7C5E),     // 2 · Muted Organic Green
    FocusBlue,             // 3 · Focus Blue (the only cool pop of color)
    Color(0xFFD8A051),     // 4 · Warm Ochre / Mustard
    ErrorCrimson,          // 5 · Deep Warm Red
    DarkWarm               // 6 · Dark Charcoal block
)

fun pieceColor(colorId: Int): Color = PieceColors.getOrElse(colorId - 1) { OliveGray }
fun pieceColorPreview(colorId: Int): Color = pieceColor(colorId).copy(alpha = 0.35f)