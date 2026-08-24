package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.AudioMobileBudget
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.compile
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealtimeAudioRendererTest {
    @Test
    fun `music commands reset one preallocated scheduler`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 128)
        val compiled = compiledTone()

        renderer.playMusic(compiled)
        renderer.playMusic(compiled)

        assertEquals(1, renderer.schedulerAllocationCount)
    }

    @Test
    fun `voice state and scratch storage are preallocated once`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 128)
        val compiled = assertIs<AudioCompilationResult.Success>(
            audioProgram {
                tempo(240f)
                instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.4f) }
                musicTrack("music") {
                    instrument("tone")
                    notes(MidiNote.of(69))
                }
                sfx("click") {
                    oscillator(OscillatorShape.SQUARE, gain = 0.4f)
                    envelope(attack = 1.ms, release = 10.ms)
                }
            }.compile(),
        ).program
        val left = FloatArray(128)
        val right = FloatArray(128)

        assertEquals(AudioMobileBudget.MAX_VOICES, renderer.voiceStateAllocationCount)
        assertEquals(AudioMobileBudget.MAX_VOICES, renderer.scratchBufferAllocationCount)
        val initialVoiceStates = renderer.voiceStateAllocationCount
        val initialScratchBuffers = renderer.scratchBufferAllocationCount

        renderer.playMusic(compiled)
        repeat(8) {
            renderer.playSfx(compiled, SfxName("click"))
            renderer.render(left, right, 128)
        }

        assertEquals(initialVoiceStates, renderer.voiceStateAllocationCount)
        assertEquals(initialScratchBuffers, renderer.scratchBufferAllocationCount)
    }

    @Test
    fun `compiled music renders stereo pcm into caller owned buffers`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 128)
        val compiled = compiledTone()
        val left = FloatArray(128)
        val right = FloatArray(128)

        renderer.playMusic(compiled)
        renderer.render(left, right, frameCount = 128)

        assertTrue(left.any { abs(it) > 0.01f })
        assertContentEquals(left, right)
    }

    @Test
    fun `background writes silence without advancing music`() {
        val paused = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 64)
        val reference = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 64)
        val compiled = compiledTone()
        val pausedLeft = FloatArray(64)
        val pausedRight = FloatArray(64)
        val referenceLeft = FloatArray(64)
        val referenceRight = FloatArray(64)
        paused.playMusic(compiled)
        reference.playMusic(compiled)

        paused.updatePolicy(AudioSessionPolicy.Background)
        paused.render(pausedLeft, pausedRight, 64)
        assertTrue(pausedLeft.all { it == 0f })

        paused.updatePolicy(AudioSessionPolicy.Active)
        paused.render(pausedLeft, pausedRight, 64)
        reference.render(referenceLeft, referenceRight, 64)

        assertContentEquals(referenceLeft, pausedLeft)
        assertContentEquals(referenceRight, pausedRight)
    }

    @Test
    fun `obscured gain keeps centered stereo channels equal`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 128)
        val left = FloatArray(128)
        val right = FloatArray(128)
        renderer.playMusic(compiledTone())
        renderer.render(left, right, 128)

        renderer.updatePolicy(AudioSessionPolicy.Obscured)
        renderer.render(left, right, 128)

        assertContentEquals(left, right)
    }

    @Test
    fun `stop fade reaches silence and clears music`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 64)
        val left = FloatArray(64)
        val right = FloatArray(64)
        renderer.playMusic(compiledTone())
        renderer.render(left, right, 64)

        renderer.stopMusic(fadeFrames = 64)
        renderer.render(left, right, 64)
        renderer.render(left, right, 64)

        assertTrue(left.all { it == 0f })
        assertTrue(right.all { it == 0f })
    }

    @Test
    fun `sfx command renders a finite sound without music`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 128)
        val left = FloatArray(128)
        val right = FloatArray(128)
        val compiled = assertIs<AudioCompilationResult.Success>(
            audioProgram {
                sfx("click") {
                    oscillator(OscillatorShape.SINE, gain = 0.5f)
                    pitch(from = 440.hz, to = 660.hz, duration = 30.ms)
                    envelope(attack = 1.ms, release = 20.ms)
                }
            }.compile(),
        ).program

        renderer.playSfx(compiled, SfxName("click"))
        renderer.render(left, right, 128)

        assertTrue(left.any { abs(it) > 0.01f })
        assertContentEquals(left, right)
    }

    @Test
    fun `music gain does not suppress the sfx bus`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 128)
        val left = FloatArray(128)
        val right = FloatArray(128)
        val compiled = assertIs<AudioCompilationResult.Success>(
            audioProgram {
                instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.4f) }
                musicTrack("music") {
                    instrument("tone")
                    notes(MidiNote.of(69))
                }
                sfx("click") {
                    oscillator(OscillatorShape.SQUARE, gain = 0.5f)
                    envelope(attack = 1.ms, release = 20.ms)
                }
            }.compile(),
        ).program
        renderer.playMusic(compiled)
        renderer.updatePolicy(AudioSessionPolicy(musicGain = 0f, acceptsNewSfx = true, schedulingPaused = false))
        renderer.render(left, right, 128)
        renderer.playSfx(compiled, SfxName("click"))

        renderer.render(left, right, 128)

        assertTrue(left.any { abs(it) > 0.01f })
    }

    @Test
    fun `sfx pitch sweep changes frequency across its declared duration`() {
        val renderer = RealtimeAudioRenderer(sampleRate = 8_000, blockCapacity = 800)
        val left = FloatArray(800)
        val right = FloatArray(800)
        val compiled = assertIs<AudioCompilationResult.Success>(
            audioProgram {
                sfx("rise") {
                    oscillator(OscillatorShape.SINE, gain = 0.5f)
                    pitch(from = 220.hz, to = 880.hz, duration = 100.ms)
                }
            }.compile(),
        ).program

        renderer.playSfx(compiled, SfxName("rise"))
        renderer.render(left, right, 800)

        assertTrue(zeroCrossings(left, 400, 800) * 2 > zeroCrossings(left, 0, 400) * 3)
    }
}

private fun zeroCrossings(samples: FloatArray, start: Int, endExclusive: Int): Int {
    var crossings = 0
    for (index in start + 1 until endExclusive) {
        if ((samples[index - 1] < 0f) != (samples[index] < 0f)) crossings += 1
    }
    return crossings
}

private fun compiledTone() = assertIs<AudioCompilationResult.Success>(
    audioProgram {
        tempo(240f)
        instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.4f) }
        musicTrack("music") {
            instrument("tone")
            notes(MidiNote.of(69))
        }
    }.compile(),
).program
