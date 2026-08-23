package ge.yet.game.miniapp.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class AudioValidationTest {
    @Test
    fun `compiler caches the runtime instrument for each sound effect`() {
        val success = assertIs<AudioCompilationResult.Success>(
            audioProgram {
                sfx("click") { oscillator(OscillatorShape.SQUARE) }
            }.compile(),
        )

        val index = success.program.soundEffectIndex(SfxName("click"))

        assertEquals(0, index)
        assertSame(
            success.program.soundEffectInstrumentAt(index),
            success.program.soundEffectInstrumentAt(index),
        )
    }

    @Test
    fun `compiler returns ordered diagnostics for unresolved and empty voices`() {
        val program = audioProgram {
            instrument("silent") {}
            musicTrack("line") {
                instrument("missing")
                notes(MidiNote.of(60))
            }
            sfx("empty") {}
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(
                AudioDiagnosticCode.EMPTY_OSCILLATOR_SOURCE to "instrument[silent].oscillators",
                AudioDiagnosticCode.UNRESOLVED_INSTRUMENT to "musicTrack[line].instrument[missing]",
                AudioDiagnosticCode.EMPTY_OSCILLATOR_SOURCE to "sfx[empty].oscillators",
            ),
            failure.diagnostics.map { it.code to it.path },
        )
    }

    @Test
    fun `compiler rejects track and oscillator budgets without partial success`() {
        val program = audioProgram {
            instrument("dense") {
                repeat(9) { oscillator(OscillatorShape.SINE) }
            }
            repeat(17) { index ->
                musicTrack("track_$index") {
                    instrument("dense")
                    notes(MidiNote.of(60))
                }
            }
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(
                AudioDiagnosticCode.OSCILLATOR_LIMIT_EXCEEDED to "instrument[dense].oscillators",
                AudioDiagnosticCode.TRACK_LIMIT_EXCEEDED to "musicTracks",
            ),
            failure.diagnostics.map { it.code to it.path },
        )
    }

    @Test
    fun `compiler returns an opaque compiled program only for a valid declaration`() {
        val program = audioProgram {
            instrument("lead") { oscillator(OscillatorShape.TRIANGLE) }
            musicTrack("melody") {
                instrument("lead")
                notes(MidiNote.of(60), MidiNote.of(64))
            }
        }

        val success = assertIs<AudioCompilationResult.Success>(program.compile())

        assertEquals(program.tempo, success.program.tempo)
        assertEquals(1, success.program.trackCount)
    }

    @Test
    fun `compiler rejects effect delay and feedback mobile budgets with stable paths`() {
        val program = audioProgram {
            instrument("lead") { oscillator(OscillatorShape.SINE) }
            musicTrack("line") {
                instrument("lead")
                notes(MidiNote.of(60))
                delay(time = 5.0.seconds, feedback = 0.96f)
                repeat(4) { reverb(send = 0.2f) }
            }
            musicBus {
                repeat(5) { reverb(send = 0.1f) }
            }
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(
                AudioDiagnosticCode.EFFECT_LIMIT_EXCEEDED to "musicBus.effects",
                AudioDiagnosticCode.DELAY_LIMIT_EXCEEDED to "musicTrack[line].effect[0].delaySeconds",
                AudioDiagnosticCode.FEEDBACK_LIMIT_EXCEEDED to "musicTrack[line].effect[0].feedback",
                AudioDiagnosticCode.EFFECT_LIMIT_EXCEEDED to "musicTrack[line].effects",
            ),
            failure.diagnostics.map { it.code to it.path },
        )
    }

    @Test
    fun `compiler bounds every repeatable voice node family`() {
        val program = audioProgram {
            instrument("dense") {
                repeat(5) { noise(NoiseColor.WHITE, seed = it.toLong()) }
                repeat(33) { partial(ratio = it + 1f) }
                repeat(5) { lowPass(cutoff = (1_000 + it).hz) }
                repeat(5) { distortion(amount = 0.1f) }
            }
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(
                AudioDiagnosticCode.VOICE_EFFECT_LIMIT_EXCEEDED to "instrument[dense].effects",
                AudioDiagnosticCode.FILTER_LIMIT_EXCEEDED to "instrument[dense].filters",
                AudioDiagnosticCode.NOISE_LIMIT_EXCEEDED to "instrument[dense].noises",
                AudioDiagnosticCode.PARTIAL_LIMIT_EXCEEDED to "instrument[dense].partials",
            ),
            failure.diagnostics.map { it.code to it.path },
        )
    }
}
