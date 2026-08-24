package ge.yet.game.miniapp.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AudioNodeDeclarationsTest {
    @Test
    fun `dsl snapshots every initial voice and effect family`() {
        val program = audioProgram {
            instrument("glass") {
                oscillator(OscillatorShape.SINE, gain = 0.4f)
                noise(NoiseColor.PINK, gain = 0.1f, seed = 42)
                partial(ratio = 2.01f, gain = 0.3f)
                frequencyModulation(ratio = 2f, index = 0.7f)
                vibrato(rate = 5.hz, depthCents = 8f)
                lowPass(cutoff = 4_000.hz, resonance = 0.2f)
                highPass(cutoff = 120.hz)
                bandPass(center = 1_200.hz, resonance = 0.5f)
                distortion(amount = 0.05f)
                bitCrush(bitDepth = 12, sampleRateReduction = 2)
            }
            musicTrack("chimes") {
                instrument("glass")
                notes(MidiNote.of(72))
                delay(time = 250.ms, feedback = 0.4f)
                reverb(send = 0.25f)
            }
            musicBus {
                reverb(send = 0.15f)
            }
            sfxBus {
                delay(time = 80.ms, feedback = 0.1f)
            }
        }

        val voice = program.instruments.single()
        assertEquals(NoiseColor.PINK, voice.noises.single().color)
        assertEquals(2.01f, voice.partials.single().ratio)
        assertEquals(2f, voice.frequencyModulation?.ratio)
        assertEquals(5.hz, voice.vibrato?.rate)
        assertEquals(3, voice.filters.size)
        assertIs<VoiceEffectDeclaration.Distortion>(voice.effects[0])
        assertIs<VoiceEffectDeclaration.BitCrush>(voice.effects[1])
        assertEquals(2, program.musicTracks.single().effects.size)
        assertEquals(1, program.musicBus.effects.size)
        assertEquals(1, program.sfxBus.effects.size)
    }

    @Test
    fun `program snapshots mutable lists nested in expanded declarations`() {
        val program = audioProgram {
            instrument("lead") {
                oscillator(OscillatorShape.SAW)
                lowPass(2_000.hz)
            }
            musicTrack("line") {
                instrument("lead")
                notes(MidiNote.of(60))
                reverb(0.2f)
            }
        }

        assertEquals(1, program.instruments.single().filters.size)
        assertEquals(1, program.musicTracks.single().effects.size)
    }
}
