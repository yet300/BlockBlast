package ge.yet.game.miniapp.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AudioValidationTest {
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
}
