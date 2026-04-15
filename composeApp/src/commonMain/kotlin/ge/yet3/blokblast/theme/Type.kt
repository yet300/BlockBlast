package ge.yet3.blokblast.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


val AnthropicSans = FontFamily.SansSerif
val AnthropicSerif = FontFamily.Serif
val AnthropicMono = FontFamily.Monospace
/**
 * BlockBlast typography scale.
 */
val Typography = Typography(

    displayLarge = TextStyle(
        fontFamily = AnthropicSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 64.sp,
        lineHeight = (64 * 1.10).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = AnthropicSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 52.sp,
        lineHeight = (52 * 1.20).sp
    ),
    titleLarge = TextStyle(
        fontFamily = AnthropicSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = (36 * 1.30).sp
    ),
    titleMedium = TextStyle(
        fontFamily = AnthropicSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = (32 * 1.10).sp
    ),
    titleSmall = TextStyle(
        fontFamily = AnthropicSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 25.sp,
        lineHeight = (25 * 1.20).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AnthropicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = (20 * 1.60).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AnthropicSans, // Body Standard
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = (16 * 1.60).sp
    ),
    bodySmall = TextStyle(
        fontFamily = AnthropicSerif, // Body Serif для статей
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = (17 * 1.60).sp
    ),
    labelMedium = TextStyle(
        fontFamily = AnthropicSans, // Caption
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = (14 * 1.43).sp
    ),
    labelSmall = TextStyle(
        fontFamily = AnthropicSans, // Label Text
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = (12 * 1.60).sp,
        letterSpacing = 0.12.sp
    )
)