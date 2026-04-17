package ge.yet3.blokblast.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Warm dark intermediates (between NearBlack and DarkSurface) ───────────────
private val Dark050 = Color(0xFF0E0E0D) // page background — a shade deeper than NearBlack
private val Dark100 = Color(0xFF1A1A18) // surfaceContainerLow
private val Dark150 = Color(0xFF1E1E1C) // surfaceContainer / secondaryContainer
private val Dark200 = Color(0xFF252523) // outlineVariant
private val Dark300 = Color(0xFF30302E) // = DarkSurface          — elevated
private val Dark400 = Color(0xFF3A3A38) // surfaceContainerHighest / surfaceBright
private val DarkTerracotta = Color(0xFF3D1A0A) // primaryContainer on dark
private val DarkCoral = Color(0xFF2D1810)      // tertiaryContainer on dark
private val DarkError = Color(0xFF2D0A0A)      // errorContainer on dark

// ─────────────────────────────────────────────────────────────────────────────

private val ClaudeLightScheme = lightColorScheme(
    // Brand
    primary            = TerracottaBrand,   // #c96442
    onPrimary          = PureWhite,
    primaryContainer   = Color(0xFFFFDDD0), // pale warm peach
    onPrimaryContainer = Color(0xFF3D1A0A),

    // Secondary
    secondary            = WarmSand,          // #e8e6dc
    onSecondary          = CharcoalWarm,       // #4d4c48
    secondaryContainer   = Ivory,             // #faf9f5
    onSecondaryContainer = OliveGray,          // #5e5d59

    // Tertiary
    tertiary            = CoralAccent,        // #d97757
    onTertiary          = PureWhite,
    tertiaryContainer   = Color(0xFFFFEDE5),  // very pale peach
    onTertiaryContainer = Color(0xFF3D1A0A),

    // Background & Surface
    background          = Parchment,          // #f5f4ed
    onBackground        = AnthropicNearBlack, // #141413
    surface             = Ivory,              // #faf9f5
    onSurface           = AnthropicNearBlack,
    surfaceVariant      = WarmSand,           // #e8e6dc
    onSurfaceVariant    = OliveGray,          // #5e5d59

    // Surface containers (used by Scaffold, TopAppBar, BottomSheet internals)
    surfaceContainerLowest  = PureWhite,
    surfaceContainerLow     = Ivory,          // #faf9f5
    surfaceContainer        = Parchment,      // #f5f4ed
    surfaceContainerHigh    = WarmSand,       // #e8e6dc
    surfaceContainerHighest = BorderWarm,     // #e8e6dc

    // Inverse (for snackbars etc.)
    inverseSurface    = AnthropicNearBlack,
    inverseOnSurface  = WarmSilver,
    inversePrimary    = CoralAccent,

    // Surface tones
    surfaceBright = PureWhite,
    surfaceDim    = WarmSand,

    // Error
    error            = ErrorCrimson,
    onError          = PureWhite,
    errorContainer   = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF2D0A0A),

    // Outlines
    outline        = BorderCream,   // #f0eee6 — subtle warm border on light
    outlineVariant = WarmSand,      // #e8e6dc

    scrim = AnthropicNearBlack,
)

private val ClaudeDarkScheme = darkColorScheme(
    // Brand
    primary            = TerracottaBrand,  // #c96442 — keeps full brand warmth
    onPrimary          = Ivory,            // #faf9f5
    primaryContainer   = DarkTerracotta,  // #3d1a0a — deep warm terracotta
    onPrimaryContainer = CoralAccent,     // #d97757

    // Secondary
    secondary            = Dark300,       // #30302e — DarkSurface
    onSecondary          = WarmSilver,    // #b0aea5
    secondaryContainer   = Dark150,       // #1e1e1c
    onSecondaryContainer = WarmSilver,

    // Tertiary
    tertiary            = CoralAccent,    // #d97757 — warm accent
    onTertiary          = Dark050,        // #141413
    tertiaryContainer   = DarkCoral,      // #2d1810
    onTertiaryContainer = CoralAccent,

    // Background & Surface
    //   background = the deep canvas; onBackground = headline text (Ivory, max contrast)
    background       = Dark050,           // #141413 — AnthropicNearBlack
    onBackground     = Ivory,             // #faf9f5 — warm white for primary text
    surface          = Dark300,           // #30302e — DarkSurface for cards/sheets
    onSurface        = Ivory,             // #faf9f5
    surfaceVariant   = Dark150,           // #1e1e1c
    onSurfaceVariant = WarmSilver,        // #b0aea5 — secondary text

    // Surface containers (used by Scaffold, TopAppBar, BottomSheet, NavigationBar…)
    surfaceContainerLowest  = Dark050,    // #141413
    surfaceContainerLow     = Dark100,    // #1a1a18
    surfaceContainer        = Dark150,    // #1e1e1c
    surfaceContainerHigh    = Dark300,    // #30302e
    surfaceContainerHighest = Dark400,    // #3a3a38

    // Surface tones
    surfaceBright = Dark400,              // #3a3a38
    surfaceDim    = Dark050,              // #141413

    // Inverse (for snackbars, tooltips on dark)
    inverseSurface   = Ivory,            // #faf9f5
    inverseOnSurface = AnthropicNearBlack,
    inversePrimary   = TerracottaBrand,

    // Error
    error            = ErrorCrimson,
    onError          = Ivory,
    errorContainer   = DarkError,        // #2d0a0a
    onErrorContainer = Color(0xFFFFB4AB),

    // Outlines — warm dark borders (NOT the light cream BorderWarm)
    outline        = Dark300,            // #30302e — visible but not harsh
    outlineVariant = Dark200,            // #252523 — subtle

    scrim = Dark050,
)


@Composable
fun BlockBlastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ClaudeDarkScheme else ClaudeLightScheme,
        typography  = Typography,
        content     = content,
    )
}
