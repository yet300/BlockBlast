package ge.yet.game.twentyfortyeight.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TileStylePolicyTest {
    @Test
    fun `board wells preserve exact themed surface tokens`() {
        assertArgb(0xFFD8D0C1u, boardWellColor(TileTheme.Light), "light board well")
        assertArgb(0xFF292826u, boardWellColor(TileTheme.Dark), "dark board well")
    }

    @Test
    fun `authored exponent stops preserve their exact colors`() {
        val lightBackgrounds = mapOf(
            1 to 0xFFECE7DCu,
            2 to 0xFFE2D7C3u,
            3 to 0xFFD3B58Au,
            4 to 0xFFC98A66u,
            7 to 0xFF9C5A44u,
            10 to 0xFF744638u,
            11 to 0xFF5E4724u,
            14 to 0xFF423B33u,
            17 to 0xFF2A2724u,
        )
        val darkBackgrounds = mapOf(
            1 to 0xFF353431u,
            2 to 0xFF403D38u,
            3 to 0xFF544A40u,
            4 to 0xFF6C5041u,
            7 to 0xFF86503Cu,
            10 to 0xFF91482Fu,
            11 to 0xFF6D541Fu,
            14 to 0xFF514034u,
            17 to 0xFF382E29u,
        )

        lightBackgrounds.forEach { (exponent, expected) ->
            val style = TileStylePolicy.style(1L shl exponent, TileTheme.Light)
            assertArgb(expected, style.background, "light background exponent $exponent")
            assertArgb(
                if (exponent <= 4) WARM_INK else IVORY,
                style.foreground,
                "light foreground exponent $exponent",
            )
        }
        darkBackgrounds.forEach { (exponent, expected) ->
            val style = TileStylePolicy.style(1L shl exponent, TileTheme.Dark)
            assertArgb(expected, style.background, "dark background exponent $exponent")
            assertArgb(IVORY, style.foreground, "dark foreground exponent $exponent")
        }
    }

    @Test
    fun `representative tile text meets WCAG AA contrast in both themes`() {
        val values = listOf(2L, 4L, 8L, 16L, 128L, 1024L, 2048L, 16384L, 131072L)

        TileTheme.entries.forEach { theme ->
            values.forEach { value ->
                val style = TileStylePolicy.style(value, theme)
                val ratio = contrastRatio(style.foreground, style.background)
                assertTrue(
                    ratio >= MIN_TEXT_CONTRAST,
                    "theme=$theme value=$value contrast=$ratio",
                )
            }
        }
    }

    @Test
    fun `theme gold outlines retain best-label contrast against their canvases`() {
        val lightGold = assertNotNull(TileStylePolicy.style(2048L, TileTheme.Light).outline)
        val darkGold = assertNotNull(TileStylePolicy.style(2048L, TileTheme.Dark).outline)

        assertArgb(LIGHT_GOLD, lightGold, "light gold")
        assertArgb(DARK_GOLD, darkGold, "dark gold")
        assertEquals(
            expected = 6.23,
            actual = contrastRatio(lightGold, Color(0xFFFAF9F5)),
            absoluteTolerance = 0.02,
        )
        assertEquals(
            expected = 6.84,
            actual = contrastRatio(darkGold, Color(0xFF30302E)),
            absoluteTolerance = 0.02,
        )
    }

    @Test
    fun `missing exponent stop is perceptually interpolated instead of snapped`() {
        val lower = TileStylePolicy.style(16L, TileTheme.Light)
        val interpolated = TileStylePolicy.style(32L, TileTheme.Light)
        val upper = TileStylePolicy.style(128L, TileTheme.Light)

        assertNotEquals(lower.background.toArgb(), interpolated.background.toArgb())
        assertNotEquals(upper.background.toArgb(), interpolated.background.toArgb())
        assertEquals(0xFF, interpolated.background.toArgb() ushr 24)
        assertTrue(contrastRatio(interpolated.foreground, interpolated.background) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun `text scale fits one through eight digits with an accessible floor`() {
        val expected = listOf(1f, 1f, 1f, 0.86f, 0.74f, 0.64f, 0.56f, 0.50f)

        assertEquals(expected, (1..8).map(TileStylePolicy::textScale))
        assertEquals(0.50f, TileStylePolicy.textScale(20))
        assertFailsWith<IllegalArgumentException> { TileStylePolicy.textScale(0) }
    }

    @Test
    fun `values above the authored range clamp color and add bounded inset marks`() {
        val exponent17 = TileStylePolicy.style(1L shl 17, TileTheme.Light)
        val exponent18 = TileStylePolicy.style(1L shl 18, TileTheme.Light)
        val exponent22 = TileStylePolicy.style(1L shl 22, TileTheme.Light)
        val exponent26 = TileStylePolicy.style(1L shl 26, TileTheme.Light)

        assertEquals(exponent17.background.toArgb(), exponent18.background.toArgb())
        assertEquals(1, exponent18.insetMarkCount)
        assertEquals(2, exponent22.insetMarkCount)
        assertEquals(3, exponent26.insetMarkCount)
    }

    @Test
    fun `style rejects values outside the engine tile domain`() {
        listOf(0L, 3L, -2L).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                TileStylePolicy.style(invalid, TileTheme.Light)
            }
        }
    }

    private fun assertArgb(expected: UInt, actual: Color, message: String) {
        assertEquals(expected.toInt(), actual.toArgb(), message)
    }

    /** Independent WCAG sRGB relative-luminance implementation used only by this test. */
    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first.toArgb())
        val secondLuminance = relativeLuminance(second.toArgb())
        val lighter = max(firstLuminance, secondLuminance)
        val darker = min(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double =
        0.2126 * linearChannel(argb ushr 16 and 0xFF) +
            0.7152 * linearChannel(argb ushr 8 and 0xFF) +
            0.0722 * linearChannel(argb and 0xFF)

    private fun linearChannel(channel: Int): Double {
        val srgb = channel / 255.0
        return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5
        const val WARM_INK = 0xFF141413u
        const val IVORY = 0xFFFAF9F5u
        const val LIGHT_GOLD = 0xFF7A5710u
        const val DARK_GOLD = 0xFFD7B769u
    }
}
