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
            setOf("drop", "merge_low", "merge_mid", "merge_high", "clear", "shake", "game_over"),
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
            FruitMergeAudio.Drop,
            FruitMergeAudio.MergeLow,
            FruitMergeAudio.MergeMid,
            FruitMergeAudio.MergeHigh,
            FruitMergeAudio.Clear,
            FruitMergeAudio.Shake,
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
}
