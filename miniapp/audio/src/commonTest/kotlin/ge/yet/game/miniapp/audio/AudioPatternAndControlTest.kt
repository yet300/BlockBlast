package ge.yet.game.miniapp.audio

import ge.yet.game.pattern.PatternQueryBudget
import ge.yet.game.pattern.TimeArc
import ge.yet.game.pattern.sequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AudioPatternAndControlTest {
    @Test
    fun `forward control references compile and preserve mapped range`() {
        val program = audioProgram {
            instrument("pad") {
                oscillator(OscillatorShape.SAW)
                lowPass(control("intensity").map(450f, 2_400f), resonance = 0.25f)
            }
            control("intensity", default = 0.3f, range = 0f..1f)
        }

        assertIs<AudioCompilationResult.Success>(program.compile())
        val cutoff = assertIs<AudioParameter.Control>(program.instruments.single().filters.single().frequency)
        assertEquals(AudioControlName("intensity"), cutoff.name)
        assertEquals(450f..2_400f, cutoff.outputRange)
    }

    @Test
    fun `unknown control returns a typed diagnostic at the parameter path`() {
        val program = audioProgram {
            instrument("pad") {
                oscillator(OscillatorShape.SAW)
                lowPass(control("missing").map(300f, 1_200f))
            }
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(AudioDiagnosticCode.UNRESOLVED_CONTROL to "instrument[pad].filter[0].cutoffHz"),
            failure.diagnostics.map { it.code to it.path },
        )
    }

    @Test
    fun `mapped filter range must remain inside the parameter domain`() {
        val program = audioProgram {
            control("intensity", default = 0.5f, range = 0f..1f)
            instrument("pad") {
                oscillator(OscillatorShape.SAW)
                lowPass(control("intensity").map(-100f, 0f))
            }
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(AudioDiagnosticCode.PARAMETER_RANGE_INVALID to "instrument[pad].filter[0].cutoffHz"),
            failure.diagnostics.map { it.code to it.path },
        )
    }

    @Test
    fun `music track owns a bounded generic pattern instead of a note list`() {
        val notePattern = sequence(List(257) { AudioNote.Pitched(MidiNote.of(60)) })
        val program = audioProgram {
            instrument("lead") { oscillator(OscillatorShape.SINE) }
            musicTrack("dense") {
                instrument("lead")
                notes(notePattern)
            }
        }

        val scheduled = program.musicTracks.single().pattern.query(
            TimeArc.unit,
            PatternQueryBudget(maxEvents = 300),
        )
        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(257, scheduled.size)
        assertEquals(
            listOf(AudioDiagnosticCode.PATTERN_EVENT_LIMIT_EXCEEDED to "musicTrack[dense].pattern"),
            failure.diagnostics.map { it.code to it.path },
        )
    }
}
