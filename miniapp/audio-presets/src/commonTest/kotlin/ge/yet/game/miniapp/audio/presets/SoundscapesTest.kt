package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.AudioProgramFragment
import ge.yet.game.miniapp.audio.audioParameter
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.testing.AudioTestRenderResult
import ge.yet.game.miniapp.audio.testing.ExperimentalMiniAppAudioTestingApi
import ge.yet.game.miniapp.audio.testing.MiniAppAudioTestRenderer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
class SoundscapesTest {
    @Test
    fun `all shared soundscapes render deterministically inside acoustic bounds`() {
        val fragments = listOf(
            OceanBreeze(seed = 11),
            SoftRain(seed = 22),
            ForestNight(seed = 33),
            DeepSpace(seed = 44),
        )

        fragments.forEach { fragment ->
            val program = program(fragment)
            val first = render(program)
            val second = render(program)

            assertEquals(first.quantizedPcmHash, second.quantizedPcmHash)
            assertTrue(first.peak >= 0.001f && first.peak < 1f)
            assertTrue(first.rms in 0.001..0.7)
            assertTrue(first.left.all(Float::isFinite))
            assertTrue(first.right.all(Float::isFinite))
            assertTrue(hasStereoMovement(first.left, first.right))
            assertTrue(program.musicTracks.size <= 16)
        }
    }

    @Test
    fun `ocean gain density and wind controls respond monotonically`() {
        val quiet = render(program(OceanBreeze(seed = 7, gain = 0.2f, density = 0f)))
        val loud = render(program(OceanBreeze(seed = 7, gain = 0.8f, density = 0f)))
        val calm = render(
            program(OceanBreeze(seed = 7, density = 0f, wind = audioParameter(0.1f))),
        )
        val windy = render(
            program(OceanBreeze(seed = 7, density = 0f, wind = audioParameter(1f))),
        )
        val sparse = program(OceanBreeze(seed = 7, density = 0f))
        val dense = program(OceanBreeze(seed = 7, density = 1f))

        assertTrue(quiet.rms < loud.rms)
        assertTrue(calm.rms < windy.rms)
        assertTrue(eventCount(sparse, "ocean_breeze_chimes") < eventCount(dense, "ocean_breeze_chimes"))
    }

    @Test
    fun `every soundscape density and stereo input changes its intended dimension`() {
        val densityCases = listOf(
            Triple(program(OceanBreeze(seed = 9, density = 0f)), program(OceanBreeze(seed = 9, density = 1f)), "ocean_breeze_chimes"),
            Triple(program(SoftRain(seed = 9, density = 0f)), program(SoftRain(seed = 9, density = 1f)), "soft_rain_drops"),
            Triple(program(ForestNight(seed = 9, density = 0f)), program(ForestNight(seed = 9, density = 1f)), "forest_night_insects"),
            Triple(program(DeepSpace(seed = 9, density = 0f)), program(DeepSpace(seed = 9, density = 1f)), "deep_space_signals"),
        )
        densityCases.forEach { (empty, full, trackName) ->
            assertTrue(eventCount(empty, trackName) < eventCount(full, trackName))
        }

        val centered = listOf(
            OceanBreeze(seed = 9, stereo = 0f),
            SoftRain(seed = 9, stereo = 0f),
            ForestNight(seed = 9, stereo = 0f),
            DeepSpace(seed = 9, stereo = 0f),
        )
        centered.forEach { fragment ->
            val pcm = render(program(fragment))
            val left = pcm.left
            val right = pcm.right
            assertTrue(left.indices.all { abs(left[it] - right[it]) < 0.000_001f })
        }
    }

    @Test
    fun `ocean layer controls scale their isolated layers monotonically`() {
        fun ocean(
            wind: Float = 0f,
            water: Float = 0f,
            waves: Float = 0f,
            chimes: Float = 0f,
        ) = program(
            OceanBreeze(
                seed = 17,
                density = 1f,
                wind = audioParameter(wind),
                water = audioParameter(water),
                waves = audioParameter(waves),
                chimes = audioParameter(chimes),
            ),
        )

        val layerPairs = listOf(
            ocean(wind = 0.1f) to ocean(wind = 1f),
            ocean(water = 0.1f) to ocean(water = 1f),
            ocean(waves = 0.1f) to ocean(waves = 1f),
            ocean(chimes = 0.1f) to ocean(chimes = 1f),
        )
        layerPairs.forEach { (quiet, loud) -> assertTrue(render(quiet).rms < render(loud).rms) }
    }

    @Test
    fun `seed changes soundscape without changing declaration budgets`() {
        val firstProgram = program(ForestNight(seed = 1))
        val secondProgram = program(ForestNight(seed = 2))

        assertTrue(render(firstProgram).quantizedPcmHash != render(secondProgram).quantizedPcmHash)
        assertEquals(firstProgram.musicTracks.size, secondProgram.musicTracks.size)
        assertEquals(firstProgram.instruments.size, secondProgram.instruments.size)
    }

    private fun program(fragment: AudioProgramFragment): AudioProgram = audioProgram {
        tempo(240f)
        include(fragment)
    }

    private fun render(program: AudioProgram) = assertIs<AudioTestRenderResult.Success>(
        MiniAppAudioTestRenderer.render(program, sampleRate = 8_000, frameCount = 16_000),
    ).pcm

    private fun eventCount(program: AudioProgram, trackName: String): Int =
        program.musicTracks.single { it.name.value == trackName }.pattern.query(
            ge.yet.game.pattern.TimeArc.unit,
            ge.yet.game.pattern.PatternQueryBudget(),
        ).size

    private fun hasStereoMovement(left: FloatArray, right: FloatArray): Boolean {
        val differences = List(4) { window ->
            val frames = window * 4_000 until (window + 1) * 4_000
            frames.sumOf { abs(left[it]).toDouble() - abs(right[it]).toDouble() }
        }
        return differences.max() - differences.min() > 5.0
    }
}
