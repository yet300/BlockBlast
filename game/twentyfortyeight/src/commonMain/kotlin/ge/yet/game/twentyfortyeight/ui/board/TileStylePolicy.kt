package ge.yet.game.twentyfortyeight.ui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ge.yet.game.twentyfortyeight.engine.TileValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class TileTheme { Light, Dark }

internal data class TileStyle(
    val background: Color,
    val foreground: Color,
    val outline: Color?,
    val insetMarkCount: Int,
    val textScale: Float,
)

internal object TileStylePolicy {
    fun style(value: Long, theme: TileTheme): TileStyle {
        TileValue(value)
        val exponent = value.countTrailingZeroBits()
        val stops = when (theme) {
            TileTheme.Light -> LightStops
            TileTheme.Dark -> DarkStops
        }
        val colorExponent = exponent.coerceIn(MIN_AUTHORED_EXPONENT, MAX_AUTHORED_EXPONENT)
        val authoredBackground = stops[colorExponent]
        val background = authoredBackground ?: interpolateBackground(
            exponent = colorExponent,
            stops = stops,
        )
        val foreground = authoredForeground(
            exponent = colorExponent,
            theme = theme,
        ) ?: compliantForeground(background)

        return TileStyle(
            background = background,
            foreground = foreground,
            outline = if (exponent == MILESTONE_EXPONENT) {
                when (theme) {
                    TileTheme.Light -> LightGold
                    TileTheme.Dark -> DarkGold
                }
            } else {
                null
            },
            insetMarkCount = insetMarkCount(exponent),
            textScale = textScale(value.toString().length),
        )
    }

    fun textScale(digits: Int): Float {
        require(digits > 0) { "Tile digit count must be positive: $digits" }
        return when (digits) {
            in 1..3 -> 1f
            4 -> 0.86f
            5 -> 0.74f
            6 -> 0.64f
            else -> max(MIN_TEXT_SCALE, 0.64f - (digits - 6) * 0.08f)
        }
    }

    private fun authoredForeground(exponent: Int, theme: TileTheme): Color? = when {
        exponent !in AuthoredExponents -> null
        theme == TileTheme.Dark -> Ivory
        exponent <= 4 -> WarmInk
        else -> Ivory
    }

    private fun compliantForeground(background: Color): Color {
        val compliant = ForegroundCandidates
            .map { color -> color to contrastRatio(color, background) }
            .filter { (_, contrast) -> contrast >= MIN_TEXT_CONTRAST }
        check(compliant.isNotEmpty()) {
            "Tile background has no WCAG AA foreground: ${background.toArgb()}"
        }
        return compliant.maxBy { (_, contrast) -> contrast }.first
    }

    private fun interpolateBackground(exponent: Int, stops: Map<Int, Color>): Color {
        val lowerExponent = stops.keys.last { it < exponent }
        val upperExponent = stops.keys.first { it > exponent }
        val progress = (exponent - lowerExponent).toDouble() / (upperExponent - lowerExponent)
        val lower = srgbToOklch(stops.getValue(lowerExponent))
        val upper = srgbToOklch(stops.getValue(upperExponent))
        val interpolated = Oklch(
            lightness = lerp(lower.lightness, upper.lightness, progress),
            chroma = lerp(lower.chroma, upper.chroma, progress),
            hue = interpolateHue(lower.hue, upper.hue, progress),
        )
        val background = oklchToSrgb(interpolated)
        return if (hasCompliantForeground(background)) {
            background
        } else {
            nearestCompliantBackground(interpolated)
        }
    }

    private fun nearestCompliantBackground(interpolated: Oklch): Color {
        val candidates = listOfNotNull(
            nearestDarkerBackground(interpolated),
            nearestLighterBackground(interpolated),
        )
        check(candidates.isNotEmpty()) { "Unable to produce a WCAG AA tile background" }
        return candidates.minWith(
            compareBy<AdjustedBackground> { abs(it.lightness - interpolated.lightness) }
                .thenBy { it.lightness },
        ).color
    }

    private fun nearestDarkerBackground(interpolated: Oklch): AdjustedBackground? {
        var compliantLightness = 0.0
        if (!hasCompliantForeground(oklchToSrgb(interpolated.copy(lightness = compliantLightness)))) {
            return null
        }
        var nonCompliantLightness = interpolated.lightness
        repeat(LIGHTNESS_SEARCH_ITERATIONS) {
            val candidateLightness = (compliantLightness + nonCompliantLightness) / 2.0
            if (hasCompliantForeground(oklchToSrgb(interpolated.copy(lightness = candidateLightness)))) {
                compliantLightness = candidateLightness
            } else {
                nonCompliantLightness = candidateLightness
            }
        }
        return AdjustedBackground(
            lightness = compliantLightness,
            color = oklchToSrgb(interpolated.copy(lightness = compliantLightness)),
        )
    }

    private fun nearestLighterBackground(interpolated: Oklch): AdjustedBackground? {
        var nonCompliantLightness = interpolated.lightness
        var compliantLightness = 1.0
        if (!hasCompliantForeground(oklchToSrgb(interpolated.copy(lightness = compliantLightness)))) {
            return null
        }
        repeat(LIGHTNESS_SEARCH_ITERATIONS) {
            val candidateLightness = (nonCompliantLightness + compliantLightness) / 2.0
            if (hasCompliantForeground(oklchToSrgb(interpolated.copy(lightness = candidateLightness)))) {
                compliantLightness = candidateLightness
            } else {
                nonCompliantLightness = candidateLightness
            }
        }
        return AdjustedBackground(
            lightness = compliantLightness,
            color = oklchToSrgb(interpolated.copy(lightness = compliantLightness)),
        )
    }

    private fun hasCompliantForeground(background: Color): Boolean =
        ForegroundCandidates.any { foreground ->
            contrastRatio(foreground, background) >= MIN_TEXT_CONTRAST
        }

    private fun insetMarkCount(exponent: Int): Int = when (exponent) {
        in 18..21 -> 1
        in 22..25 -> 2
        in 26..Int.MAX_VALUE -> 3
        else -> 0
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = max(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * srgbToLinear((argb ushr 16 and 0xFF) / 255.0) +
            0.7152 * srgbToLinear((argb ushr 8 and 0xFF) / 255.0) +
            0.0722 * srgbToLinear((argb and 0xFF) / 255.0)
    }

    private fun srgbToOklch(color: Color): Oklch {
        val red = srgbToLinear(color.red.toDouble())
        val green = srgbToLinear(color.green.toDouble())
        val blue = srgbToLinear(color.blue.toDouble())

        val l = 0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue
        val m = 0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue
        val s = 0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue
        val lRoot = l.pow(1.0 / 3.0)
        val mRoot = m.pow(1.0 / 3.0)
        val sRoot = s.pow(1.0 / 3.0)
        val lightness = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot
        val a = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot
        val b = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
        return Oklch(
            lightness = lightness,
            chroma = sqrt(a * a + b * b),
            hue = atan2(b, a),
        )
    }

    private fun oklchToSrgb(color: Oklch): Color {
        val a = color.chroma * cos(color.hue)
        val b = color.chroma * sin(color.hue)
        val lRoot = color.lightness + 0.3963377774 * a + 0.2158037573 * b
        val mRoot = color.lightness - 0.1055613458 * a - 0.0638541728 * b
        val sRoot = color.lightness - 0.0894841775 * a - 1.2914855480 * b
        val l = lRoot * lRoot * lRoot
        val m = mRoot * mRoot * mRoot
        val s = sRoot * sRoot * sRoot
        val red = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val green = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val blue = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return Color(
            red = linearToSrgb(red).coerceIn(0.0, 1.0).toFloat(),
            green = linearToSrgb(green).coerceIn(0.0, 1.0).toFloat(),
            blue = linearToSrgb(blue).coerceIn(0.0, 1.0).toFloat(),
            alpha = 1f,
        )
    }

    private fun srgbToLinear(channel: Double): Double = if (channel <= 0.04045) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(channel: Double): Double = if (channel <= 0.0031308) {
        12.92 * channel
    } else {
        1.055 * channel.pow(1.0 / 2.4) - 0.055
    }

    private fun interpolateHue(first: Double, second: Double, progress: Double): Double {
        var delta = second - first
        while (delta > PI) delta -= 2.0 * PI
        while (delta < -PI) delta += 2.0 * PI
        return first + delta * progress
    }

    private fun lerp(first: Double, second: Double, progress: Double): Double =
        first + (second - first) * progress

    private data class Oklch(
        val lightness: Double,
        val chroma: Double,
        val hue: Double,
    )

    private data class AdjustedBackground(
        val lightness: Double,
        val color: Color,
    )

    private const val MIN_AUTHORED_EXPONENT = 1
    private const val MAX_AUTHORED_EXPONENT = 17
    private const val MILESTONE_EXPONENT = 11
    private const val MIN_TEXT_CONTRAST = 4.5
    private const val MIN_TEXT_SCALE = 0.50f
    private const val LIGHTNESS_SEARCH_ITERATIONS = 48

    private val AuthoredExponents = setOf(1, 2, 3, 4, 7, 10, 11, 14, 17)
    private val WarmInk = Color(0xFF141413)
    private val Ivory = Color(0xFFFAF9F5)
    private val ForegroundCandidates = listOf(WarmInk, Ivory)
    private val LightGold = Color(0xFF7A5710)
    private val DarkGold = Color(0xFFD7B769)

    private val LightStops = mapOf(
        1 to Color(0xFFECE7DC),
        2 to Color(0xFFE2D7C3),
        3 to Color(0xFFD3B58A),
        4 to Color(0xFFC98A66),
        7 to Color(0xFF9C5A44),
        10 to Color(0xFF744638),
        11 to Color(0xFF5E4724),
        14 to Color(0xFF423B33),
        17 to Color(0xFF2A2724),
    )
    private val DarkStops = mapOf(
        1 to Color(0xFF353431),
        2 to Color(0xFF403D38),
        3 to Color(0xFF544A40),
        4 to Color(0xFF6C5041),
        7 to Color(0xFF86503C),
        10 to Color(0xFF91482F),
        11 to Color(0xFF6D541F),
        14 to Color(0xFF514034),
        17 to Color(0xFF382E29),
    )
}
