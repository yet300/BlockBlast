package ge.yet.game.fruitmerge.audio

import ge.yet.game.miniapp.audio.testing.AudioTestRenderRequest
import ge.yet.game.miniapp.audio.testing.AudioTestRenderResult
import ge.yet.game.miniapp.audio.testing.AudioTestSfxTrigger
import ge.yet.game.miniapp.audio.testing.ExperimentalMiniAppAudioTestingApi
import ge.yet.game.miniapp.audio.testing.MiniAppAudioTestRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
class FruitMergeAudioTest {
    @Test
    fun `program exposes the original crate groove and exact SFX contract`() {
        val program = FruitMergeAudio.program

        assertEquals(84f, program.tempo.bpm)
        assertEquals(
            listOf("crate_knocks", "fruit_rolls", "glass_sprinkles"),
            program.musicTracks.map { track -> track.name.value },
        )
        assertEquals(
            setOf(
                "release",
                "landing_small",
                "landing_medium",
                "landing_heavy",
                "merge_low",
                "merge_mid",
                "merge_high",
                "clear_slice",
                "shake_left",
                "shake_right",
                "danger_enter",
                "game_over",
            ),
            program.soundEffects.mapTo(linkedSetOf()) { effect -> effect.name.value },
        )
    }

    @Test
    fun `crate groove compiles to finite bounded audible stereo PCM`() {
        val result = MiniAppAudioTestRenderer.render(
            FruitMergeAudio.program,
            AudioTestRenderRequest(sampleRate = 24_000, frameCount = 12_000),
        )
        val pcm = assertIs<AudioTestRenderResult.Success>(result).pcm

        assertTrue(pcm.left.all(Float::isFinite))
        assertTrue(pcm.right.all(Float::isFinite))
        assertTrue(pcm.rms > 0.0001)
        assertTrue(pcm.peak < 0.98f)
        assertTrue(pcm.left.any { sample -> sample != 0f })
        assertTrue(pcm.right.any { sample -> sample != 0f })
    }

    @Test
    fun `every SFX is deterministic finite audible and below clipping`() {
        listOf(
            FruitMergeAudio.Release,
            FruitMergeAudio.LandingSmall,
            FruitMergeAudio.LandingMedium,
            FruitMergeAudio.LandingHeavy,
            FruitMergeAudio.MergeLow,
            FruitMergeAudio.MergeMid,
            FruitMergeAudio.MergeHigh,
            FruitMergeAudio.ClearSlice,
            FruitMergeAudio.ShakeLeft,
            FruitMergeAudio.ShakeRight,
            FruitMergeAudio.DangerEnter,
            FruitMergeAudio.GameOver,
        ).forEach { name ->
            fun render() = assertIs<AudioTestRenderResult.Success>(
                MiniAppAudioTestRenderer.render(
                    FruitMergeAudio.program,
                    AudioTestRenderRequest(
                        sampleRate = 24_000,
                        frameCount = 16_000,
                        includeMusic = false,
                        sfxTriggers = listOf(AudioTestSfxTrigger(name, frameOffset = 0)),
                    ),
                ),
            ).pcm

            val first = render()
            val second = render()
            assertEquals(first.quantizedPcmHash, second.quantizedPcmHash, name.value)
            assertTrue(first.left.all(Float::isFinite), name.value)
            assertTrue(first.right.all(Float::isFinite), name.value)
            assertTrue(first.rms > 0.0001, name.value)
            assertTrue(first.peak < 0.98f, name.value)
        }
    }

    @Test
    fun `landing has a soft impact tail while clear carries a faster slice texture`() {
        val drop = renderSfx(FruitMergeAudio.LandingMedium)
        val clear = renderSfx(FruitMergeAudio.ClearSlice)

        assertTrue(windowRms(drop.left, 0, 1_200) > windowRms(drop.left, 3_600, 4_800))
        assertTrue(windowRms(drop.left, 1_800, 3_000) > 0.0001f)
        assertTrue(zeroCrossings(clear.left, 0, 2_400) > zeroCrossings(drop.left, 0, 2_400))
        assertTrue(windowRms(clear.left, 0, 1_200) > windowRms(clear.left, 3_600, 4_800))
    }

    @Test
    fun `rapid tactile overlaps retain audible headroom`() {
        val result = MiniAppAudioTestRenderer.render(
            FruitMergeAudio.program,
            AudioTestRenderRequest(
                sampleRate = 24_000,
                frameCount = 9_600,
                includeMusic = false,
                sfxTriggers = listOf(
                    AudioTestSfxTrigger(FruitMergeAudio.LandingHeavy, frameOffset = 0),
                    AudioTestSfxTrigger(FruitMergeAudio.MergeHigh, frameOffset = 480),
                    AudioTestSfxTrigger(FruitMergeAudio.ShakeLeft, frameOffset = 900),
                    AudioTestSfxTrigger(FruitMergeAudio.ShakeRight, frameOffset = 1_320),
                ),
            ),
        )
        val pcm = assertIs<AudioTestRenderResult.Success>(result).pcm

        assertTrue(pcm.rms > 0.0001)
        assertTrue(pcm.peak < 0.98f)
    }

    private fun renderSfx(name: ge.yet.game.miniapp.audio.SfxName) =
        assertIs<AudioTestRenderResult.Success>(
            MiniAppAudioTestRenderer.render(
                FruitMergeAudio.program,
                AudioTestRenderRequest(
                    sampleRate = 24_000,
                    frameCount = 4_800,
                    includeMusic = false,
                    sfxTriggers = listOf(AudioTestSfxTrigger(name, frameOffset = 0)),
                ),
            ),
        ).pcm

    private fun windowRms(samples: FloatArray, start: Int, end: Int): Float {
        var sum = 0.0
        for (index in start until end) sum += samples[index] * samples[index]
        return kotlin.math.sqrt(sum / (end - start)).toFloat()
    }

    private fun zeroCrossings(samples: FloatArray, start: Int, end: Int): Int {
        var count = 0
        for (index in start + 1 until end) {
            if ((samples[index - 1] < 0f) != (samples[index] < 0f)) count += 1
        }
        return count
    }
}
