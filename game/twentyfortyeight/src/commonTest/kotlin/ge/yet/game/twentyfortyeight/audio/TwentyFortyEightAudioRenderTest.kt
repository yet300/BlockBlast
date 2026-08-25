package ge.yet.game.twentyfortyeight.audio

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.testing.AudioTestControlValue
import ge.yet.game.miniapp.audio.testing.AudioTestPcm
import ge.yet.game.miniapp.audio.testing.AudioTestRenderRequest
import ge.yet.game.miniapp.audio.testing.AudioTestRenderResult
import ge.yet.game.miniapp.audio.testing.AudioTestSfxTrigger
import ge.yet.game.miniapp.audio.testing.ExperimentalMiniAppAudioTestingApi
import ge.yet.game.miniapp.audio.testing.MiniAppAudioTestRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
class TwentyFortyEightAudioRenderTest {
    @Test
    fun `program compiles inside mobile budgets on the realtime render path`() {
        val pcm = renderSuccess(
            AudioTestRenderRequest(
                sampleRate = SAMPLE_RATE,
                frameCount = SPECTRAL_WINDOW_FRAMES,
            ),
        )

        assertEquals(SAMPLE_RATE, pcm.sampleRate)
        assertEquals(SPECTRAL_WINDOW_FRAMES, pcm.frameCount)
        assertTrue(pcm.left.any { it != 0f })
    }

    @Test
    fun `every authored SFX is deterministic finite audible stereo and below clipping`() {
        APPROVED_SFX.forEach { name ->
            val first = renderSfx(name)
            val second = renderSfx(name)
            val firstLeft = first.left
            val firstRight = first.right

            assertEquals(first.quantizedPcmHash, second.quantizedPcmHash, name.value)
            assertContentEquals(firstLeft, second.left, name.value)
            assertContentEquals(firstRight, second.right, name.value)
            assertTrue(firstLeft.all(Float::isFinite), "${name.value}: left PCM must be finite")
            assertTrue(firstRight.all(Float::isFinite), "${name.value}: right PCM must be finite")
            assertTrue(first.rms > MIN_AUDIBLE_RMS, "${name.value}: RMS ${first.rms}")
            assertTrue(channelRms(firstLeft) > MIN_CHANNEL_RMS, "${name.value}: left channel is silent")
            assertTrue(channelRms(firstRight) > MIN_CHANNEL_RMS, "${name.value}: right channel is silent")
            assertTrue(first.peak < MAX_PEAK, "${name.value}: peak ${first.peak}")
        }
    }

    @Test
    fun `merge tiers keep increasing spectral centroids`() {
        val low = spectralCentroid(renderSfx(TwentyFortyEightAudio.MergeLow))
        val mid = spectralCentroid(renderSfx(TwentyFortyEightAudio.MergeMid))
        val high = spectralCentroid(renderSfx(TwentyFortyEightAudio.MergeHigh))

        assertTrue(low < mid, "Expected merge_low centroid $low below merge_mid $mid")
        assertTrue(mid < high, "Expected merge_mid centroid $mid below merge_high $high")
    }

    @Test
    fun `undo rises while game over falls in windowed pitch`() {
        val undo = renderSfx(TwentyFortyEightAudio.Undo)
        val undoEarly = dominantFrequency(undo, startFrame = 128, windowFrames = PITCH_WINDOW_FRAMES)
        val undoLate = dominantFrequency(undo, startFrame = 1_280, windowFrames = PITCH_WINDOW_FRAMES)
        val gameOver = renderSfx(TwentyFortyEightAudio.GameOver)
        val gameOverEarly = dominantFrequency(gameOver, startFrame = 256, windowFrames = PITCH_WINDOW_FRAMES)
        val gameOverLate = dominantFrequency(gameOver, startFrame = 3_200, windowFrames = PITCH_WINDOW_FRAMES)

        assertTrue(undoEarly < undoLate, "Expected Undo to rise: $undoEarly -> $undoLate")
        assertTrue(gameOverEarly > gameOverLate, "Expected Game Over to fall: $gameOverEarly -> $gameOverLate")
    }

    @Test
    fun `each control independently and deterministically changes music PCM`() {
        fun render(controls: List<AudioTestControlValue>): AudioTestPcm = renderSuccess(
            AudioTestRenderRequest(
                sampleRate = SAMPLE_RATE,
                frameCount = MUSIC_FRAME_COUNT,
                controls = controls,
            ),
        )
        val baselineControls = listOf(
            AudioTestControlValue(TwentyFortyEightAudio.Progress, 0f),
            AudioTestControlValue(TwentyFortyEightAudio.Danger, 0f),
            AudioTestControlValue(TwentyFortyEightAudio.Momentum, 0f),
        )
        val variants = listOf(
            "progress" to baselineControls.withControl(TwentyFortyEightAudio.Progress, 1f),
            "danger" to baselineControls.withControl(TwentyFortyEightAudio.Danger, 1f),
            "momentum" to baselineControls.withControl(TwentyFortyEightAudio.Momentum, 1f),
        )

        val baseline = render(baselineControls)
        val baselineAgain = render(baselineControls)
        assertDeterministic("baseline controls", baseline, baselineAgain)
        assertPcmHealth("baseline controls", baseline)

        variants.forEach { (name, controls) ->
            val changed = render(controls)
            val changedAgain = render(controls)
            assertDeterministic("$name control", changed, changedAgain)
            assertNotEquals(baseline.quantizedPcmHash, changed.quantizedPcmHash, name)
            assertTrue(
                !baseline.left.contentEquals(changed.left) || !baseline.right.contentEquals(changed.right),
                "$name must change the PCM stream from the common baseline",
            )
            assertPcmHealth("$name control", changed)
        }
    }

    @Test
    fun `rapid ordered move merge and spawn mix stays deterministic and bounded`() {
        fun render(vararg triggers: AudioTestSfxTrigger): AudioTestPcm = renderSuccess(
            AudioTestRenderRequest(
                sampleRate = SAMPLE_RATE,
                frameCount = SFX_FRAME_COUNT,
                includeMusic = false,
                sfxTriggers = triggers.toList(),
            ),
        )
        val move = AudioTestSfxTrigger(TwentyFortyEightAudio.Move, frameOffset = 0)
        val merge = AudioTestSfxTrigger(TwentyFortyEightAudio.MergeHigh, frameOffset = MERGE_OFFSET)
        val spawn = AudioTestSfxTrigger(TwentyFortyEightAudio.TileSpawn, frameOffset = SPAWN_OFFSET)

        val moveOnly = render(move)
        val moveAndMerge = render(move, merge)
        val full = render(move, merge, spawn)
        val fullAgain = render(move, merge, spawn)

        assertStereoPrefixEquals(moveOnly, moveAndMerge, MERGE_OFFSET, "merge boundary")
        assertStereoDiffersAfter(moveOnly, moveAndMerge, MERGE_OFFSET, "merge trigger")
        assertStereoPrefixEquals(moveAndMerge, full, SPAWN_OFFSET, "spawn boundary")
        assertStereoDiffersAfter(moveAndMerge, full, SPAWN_OFFSET, "spawn trigger")
        assertDeterministic("rapid move merge spawn", full, fullAgain)
        assertPcmHealth("rapid move merge spawn", full)
    }

    private fun List<AudioTestControlValue>.withControl(
        name: AudioControlName,
        value: Float,
    ): List<AudioTestControlValue> = map { control ->
        if (control.name == name) AudioTestControlValue(name, value) else control
    }

    private fun assertDeterministic(label: String, first: AudioTestPcm, second: AudioTestPcm) {
        assertEquals(first.quantizedPcmHash, second.quantizedPcmHash, label)
        assertContentEquals(first.left, second.left, label)
        assertContentEquals(first.right, second.right, label)
    }

    private fun assertStereoPrefixEquals(
        expected: AudioTestPcm,
        actual: AudioTestPcm,
        endExclusive: Int,
        label: String,
    ) {
        assertContentEquals(expected.left.copyOfRange(0, endExclusive), actual.left.copyOfRange(0, endExclusive), label)
        assertContentEquals(
            expected.right.copyOfRange(0, endExclusive),
            actual.right.copyOfRange(0, endExclusive),
            label,
        )
    }

    private fun assertStereoDiffersAfter(
        before: AudioTestPcm,
        after: AudioTestPcm,
        startInclusive: Int,
        label: String,
    ) {
        val beforeLeft = before.left
        val beforeRight = before.right
        val afterLeft = after.left
        val afterRight = after.right
        assertTrue(
            (startInclusive until before.frameCount).any { frame -> beforeLeft[frame] != afterLeft[frame] },
            "$label must change left samples at or after frame $startInclusive",
        )
        assertTrue(
            (startInclusive until before.frameCount).any { frame -> beforeRight[frame] != afterRight[frame] },
            "$label must change right samples at or after frame $startInclusive",
        )
    }

    private fun renderSfx(name: SfxName): AudioTestPcm = renderSuccess(
        AudioTestRenderRequest(
            sampleRate = SAMPLE_RATE,
            frameCount = SFX_FRAME_COUNT,
            includeMusic = false,
            sfxTriggers = listOf(AudioTestSfxTrigger(name, frameOffset = 0)),
        ),
    )

    private fun renderSuccess(request: AudioTestRenderRequest): AudioTestPcm {
        val result = MiniAppAudioTestRenderer.render(TwentyFortyEightAudio.program, request)
        return assertIs<AudioTestRenderResult.Success>(result).pcm
    }

    private fun assertPcmHealth(label: String, pcm: AudioTestPcm) {
        val left = pcm.left
        val right = pcm.right
        assertTrue(left.all(Float::isFinite), "$label: left PCM must be finite")
        assertTrue(right.all(Float::isFinite), "$label: right PCM must be finite")
        assertTrue(channelRms(left) > MIN_CHANNEL_RMS, "$label: left channel is silent")
        assertTrue(channelRms(right) > MIN_CHANNEL_RMS, "$label: right channel is silent")
        assertTrue(pcm.peak < MAX_PEAK, "$label: peak ${pcm.peak}")
    }

    private fun channelRms(samples: FloatArray): Double {
        var sum = 0.0
        for (sample in samples) sum += sample * sample
        return sqrt(sum / samples.size)
    }

    private fun spectralCentroid(pcm: AudioTestPcm): Double {
        val samples = monoWindow(pcm, startFrame = 0, frameCount = SPECTRAL_WINDOW_FRAMES)
        var weightedFrequencies = 0.0
        var magnitudes = 0.0
        for (bin in 1..samples.size / 2) {
            val magnitude = dftMagnitude(samples, bin)
            val frequency = bin.toDouble() * pcm.sampleRate / samples.size
            weightedFrequencies += frequency * magnitude
            magnitudes += magnitude
        }
        return weightedFrequencies / magnitudes
    }

    private fun dominantFrequency(
        pcm: AudioTestPcm,
        startFrame: Int,
        windowFrames: Int,
    ): Double {
        val samples = monoWindow(pcm, startFrame, windowFrames)
        val minimumBin = (MIN_PITCH_HZ * samples.size / pcm.sampleRate).toInt().coerceAtLeast(1)
        val maximumBin = (MAX_PITCH_HZ * samples.size / pcm.sampleRate).toInt()
            .coerceAtMost(samples.size / 2)
        var dominantBin = minimumBin
        var dominantMagnitude = Double.NEGATIVE_INFINITY
        for (bin in minimumBin..maximumBin) {
            val magnitude = dftMagnitude(samples, bin)
            if (magnitude > dominantMagnitude) {
                dominantMagnitude = magnitude
                dominantBin = bin
            }
        }
        return dominantBin.toDouble() * pcm.sampleRate / samples.size
    }

    private fun monoWindow(
        pcm: AudioTestPcm,
        startFrame: Int,
        frameCount: Int,
    ): DoubleArray {
        require(startFrame >= 0 && startFrame + frameCount <= pcm.frameCount)
        val left = pcm.left
        val right = pcm.right
        return DoubleArray(frameCount) { frame ->
            val hann = 0.5 - 0.5 * cos(2.0 * PI * frame / (frameCount - 1))
            ((left[startFrame + frame] + right[startFrame + frame]) * 0.5) * hann
        }
    }

    private fun dftMagnitude(samples: DoubleArray, bin: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        val radiansPerFrame = 2.0 * PI * bin / samples.size
        for (frame in samples.indices) {
            val phase = radiansPerFrame * frame
            real += samples[frame] * cos(phase)
            imaginary -= samples[frame] * kotlin.math.sin(phase)
        }
        return sqrt(real * real + imaginary * imaginary)
    }

    private companion object {
        val APPROVED_SFX = listOf(
            SfxName("tile_spawn"),
            SfxName("move"),
            SfxName("merge_low"),
            SfxName("merge_mid"),
            SfxName("merge_high"),
            SfxName("undo"),
            SfxName("new_best"),
            SfxName("victory"),
            SfxName("game_over"),
        )
        const val SAMPLE_RATE = 16_000
        const val SFX_FRAME_COUNT = 16_000
        const val MUSIC_FRAME_COUNT = SAMPLE_RATE * 8
        const val SPECTRAL_WINDOW_FRAMES = 2_048
        const val PITCH_WINDOW_FRAMES = 768
        const val MERGE_OFFSET = 64
        const val SPAWN_OFFSET = 128
        const val MIN_PITCH_HZ = 70.0
        const val MAX_PITCH_HZ = 2_000.0
        const val MIN_AUDIBLE_RMS = 0.0001
        const val MIN_CHANNEL_RMS = 0.00005
        const val MAX_PEAK = 0.95f
    }
}
