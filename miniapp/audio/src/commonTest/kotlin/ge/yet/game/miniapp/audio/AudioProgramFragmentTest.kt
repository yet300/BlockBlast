package ge.yet.game.miniapp.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AudioProgramFragmentTest {
    @Test
    fun `include merges a fragment without overriding program tempo`() {
        val fragment = audioProgramFragment {
            control("tone", default = 0.4f, range = 0f..1f)
            instrument("lead") {
                oscillator(OscillatorShape.SINE)
                lowPass(control("tone").map(300f, 3_000f))
            }
            sfx("click") {
                oscillator(OscillatorShape.SQUARE)
                envelope(attack = 1.ms, release = 20.ms)
            }
        }

        val program = audioProgram {
            tempo(180f)
            include(fragment)
        }

        assertEquals(180f, program.tempo.bpm)
        assertEquals(listOf("tone"), program.controls.map { it.name.value })
        assertEquals(listOf("lead"), program.instruments.map { it.name.value })
        assertEquals(listOf("click"), program.soundEffects.map { it.name.value })
    }

    @Test
    fun `fragment snapshots mutable author input when it is created`() {
        val notes = mutableListOf(MidiNote.of(60))
        val fragment = audioProgramFragment {
            instrument("lead") { oscillator(OscillatorShape.SINE) }
            musicTrack("line") {
                instrument("lead")
                notes(notes)
            }
        }
        notes += MidiNote.of(72)

        val program = audioProgram { include(fragment) }

        assertEquals(
            1,
            program.musicTracks.single().pattern.query(
                ge.yet.game.pattern.TimeArc.unit,
                ge.yet.game.pattern.PatternQueryBudget(),
            ).size,
        )
    }

    @Test
    fun `nested fragments compose and duplicate names remain rejected`() {
        val instrument = audioProgramFragment {
            instrument("lead") { oscillator(OscillatorShape.SINE) }
        }
        val composed = audioProgramFragment {
            include(instrument)
            sfx("click") { oscillator(OscillatorShape.SQUARE) }
        }

        assertEquals(1, audioProgram { include(composed) }.instruments.size)
        assertFailsWith<IllegalArgumentException> {
            audioProgram {
                include(instrument)
                include(instrument)
            }
        }
    }

    @Test
    fun `failed include does not leave declarations merged before the conflict`() {
        val fragment = audioProgramFragment {
            control("tone", default = 0.5f, range = 0f..1f)
            instrument("lead") { oscillator(OscillatorShape.SINE) }
        }

        val program = audioProgram {
            instrument("lead") { oscillator(OscillatorShape.SQUARE) }
            runCatching { include(fragment) }
        }

        assertEquals(emptyList(), program.controls)
        assertEquals(OscillatorShape.SQUARE, program.instruments.single().oscillators.single().shape)
    }
}
