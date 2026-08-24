package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.compile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosPcmProducerTest {
    @Test
    fun `buffer policy separates producer quantum from maximum callback frames`() {
        assertEquals(
            IosPcmBufferConfiguration(
                producerQuantum = 256,
                ringCapacity = 2_048,
                startWatermark = 768,
                targetWatermark = 1_536,
            ),
            IosPcmBufferConfiguration.select(maximumFramesPerSlice = 256),
        )
        assertEquals(
            IosPcmBufferConfiguration(
                producerQuantum = 512,
                ringCapacity = 4_096,
                startWatermark = 4_096,
                targetWatermark = 4_096,
            ),
            IosPcmBufferConfiguration.select(maximumFramesPerSlice = 4_096),
        )
        assertFailsWith<IllegalArgumentException> {
            IosPcmBufferConfiguration.select(maximumFramesPerSlice = 0)
        }
    }

    @Test
    fun `prefill consumes commands and stops at target watermark`() {
        val renderer = ProducerRecordingRenderer()
        val producer = producer(renderer, maximumFramesPerSlice = 64)

        assertEquals(
            AudioRuntimeSubmitResult.Accepted,
            producer.submit(AudioCommand.PlayMusic(compiledProducerTone())),
        )
        assertEquals(0, renderer.playMusicCount)

        assertTrue(producer.resumeAndAwaitPrefill())
        assertEquals(1, renderer.playMusicCount)
        assertEquals(192, producer.bufferedFrames)
        while (producer.pumpOnce()) {
            // Deterministically fill to the target watermark.
        }

        assertEquals(384, producer.bufferedFrames)
        assertEquals(6, renderer.renderCount)
        assertFalse(producer.pumpOnce())
        assertEquals(6, renderer.renderCount)
    }

    @Test
    fun `policy is applied by producer work and pause clears buffered pcm`() {
        val renderer = ProducerRecordingRenderer()
        val producer = producer(renderer, maximumFramesPerSlice = 64)
        producer.submit(AudioCommand.PlayMusic(compiledProducerTone()))
        producer.updatePolicy(AudioSessionPolicy.Obscured)

        assertEquals(emptyList(), renderer.policies)
        assertTrue(producer.resumeAndAwaitPrefill())
        assertEquals(listOf(AudioSessionPolicy.Obscured), renderer.policies)
        assertTrue(producer.bufferedFrames > 0)

        producer.pauseAndReset()

        assertEquals(0, producer.bufferedFrames)
        assertFalse(producer.pumpOnce())
    }

    @Test
    fun `renderer failure publishes no partial block and becomes terminal`() {
        val renderer = ProducerRecordingRenderer(failRender = true)
        val producer = producer(renderer, maximumFramesPerSlice = 64)
        producer.submit(AudioCommand.PlayMusic(compiledProducerTone()))

        assertFalse(producer.resumeAndAwaitPrefill())
        assertEquals(0, producer.bufferedFrames)
        assertEquals(1L, producer.drainProducerDiagnostics().renderFailures)
        assertEquals(
            AudioRuntimeSubmitResult.RejectedDestroyed,
            producer.submit(AudioCommand.SetControl(AudioControlName("later"), 0.5f)),
        )

        producer.terminate()
        producer.terminate()
        assertEquals(1, renderer.destroyCount)
    }

    @Test
    fun `full target prevents additional renderer work`() {
        val renderer = ProducerRecordingRenderer()
        val producer = producer(renderer, maximumFramesPerSlice = 4_096)
        producer.submit(AudioCommand.PlayMusic(compiledProducerTone()))

        assertTrue(producer.resumeAndAwaitPrefill())

        assertEquals(4_096, producer.bufferedFrames)
        assertEquals(8, renderer.renderCount)
        assertFalse(producer.pumpOnce())
        assertEquals(8, renderer.renderCount)
    }

    private fun producer(
        renderer: ProducerRecordingRenderer,
        maximumFramesPerSlice: Int,
    ) = DefaultIosPcmProducer(
        sampleRate = 48_000,
        maximumFramesPerSlice = maximumFramesPerSlice,
        rendererFactory = IosAudioRendererFactory { _, frameCapacity ->
            renderer.also { it.frameCapacity = frameCapacity }
        },
    )
}

private class ProducerRecordingRenderer(
    private val failRender: Boolean = false,
) : IosAudioRenderer {
    var frameCapacity: Int = 0
    var playMusicCount: Int = 0
    var renderCount: Int = 0
    var destroyCount: Int = 0
    val policies = mutableListOf<AudioSessionPolicy>()

    override fun updatePolicy(policy: AudioSessionPolicy) {
        policies += policy
    }

    override fun render(left: FloatArray, right: FloatArray, frameCount: Int) {
        check(frameCount <= frameCapacity)
        renderCount += 1
        left.fill(renderCount.toFloat(), 0, frameCount)
        right.fill(-renderCount.toFloat(), 0, frameCount)
        if (failRender) error("synthetic producer render failure")
    }

    override fun playMusic(program: CompiledAudioProgram): AudioRuntimeCommandOutcome {
        playMusicCount += 1
        return AudioRuntimeCommandOutcome.APPLIED
    }

    override fun stopMusic(fadeFrames: Int): AudioRuntimeCommandOutcome = AudioRuntimeCommandOutcome.APPLIED

    override fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeCommandOutcome =
        AudioRuntimeCommandOutcome.APPLIED

    override fun setControl(name: AudioControlName, value: Float): AudioRuntimeCommandOutcome =
        AudioRuntimeCommandOutcome.APPLIED

    override fun destroy(): AudioRuntimeCommandOutcome {
        destroyCount += 1
        return AudioRuntimeCommandOutcome.APPLIED
    }
}

private fun compiledProducerTone() = assertIs<AudioCompilationResult.Success>(
    audioProgram {
        tempo(240f)
        instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.4f) }
        musicTrack("music") {
            instrument("tone")
            notes(MidiNote.of(69))
        }
    }.compile(),
).program
