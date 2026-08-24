package ge.yet.game.miniapp.audio

import ge.yet.game.pattern.PatternQueryBudget
import ge.yet.game.pattern.TimeArc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AudioProgramDslTest {
    @Test
    fun `builder snapshots controls instruments tracks and sfx`() {
        val program = audioProgram {
            tempo(112f)
            control("intensity", default = 0.25f, range = 0f..1f)
            instrument("bass") {
                oscillator(OscillatorShape.SAW, gain = 0.8f, detuneCents = -4f)
                envelope(attack = 8.ms, decay = 140.ms, sustain = 0.45f, release = 180.ms)
            }
            musicTrack("bassline") {
                instrument("bass")
                notes(MidiNote.of(36), MidiNote.of(36), MidiNote.of(39))
            }
            sfx("place") {
                oscillator(OscillatorShape.SINE)
                pitch(from = 240.hz, to = 120.hz, duration = 55.ms)
                envelope(attack = 1.ms, release = 60.ms)
            }
        }

        assertEquals(Tempo.of(112f), program.tempo)
        assertEquals(listOf(AudioControlName("intensity")), program.controls.map { it.name })
        assertEquals(listOf(InstrumentName("bass")), program.instruments.map { it.name })
        assertEquals(listOf(MusicTrackName("bassline")), program.musicTracks.map { it.name })
        assertEquals(listOf(SfxName("place")), program.soundEffects.map { it.name })
        assertEquals(
            3,
            program.musicTracks.single().pattern.query(TimeArc.unit, PatternQueryBudget()).size,
        )
    }

    @Test
    fun `built program is isolated from mutable author inputs`() {
        val notes = mutableListOf(MidiNote.of(36))
        val program = audioProgram {
            instrument("bass") { oscillator(OscillatorShape.SINE) }
            musicTrack("line") {
                instrument("bass")
                notes(notes)
            }
        }

        notes += MidiNote.of(48)

        assertEquals(
            listOf(AudioNote.Pitched(MidiNote.of(36))),
            program.musicTracks.single().pattern.query(TimeArc.unit, PatternQueryBudget()).map { it.value },
        )
    }

    @Test
    fun `builder rejects duplicate declaration names immediately`() {
        assertFailsWith<IllegalArgumentException> {
            audioProgram {
                control("intensity", 0.2f, 0f..1f)
                control("intensity", 0.3f, 0f..1f)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            audioProgram {
                instrument("lead") { oscillator(OscillatorShape.SINE) }
                instrument("lead") { oscillator(OscillatorShape.SAW) }
            }
        }
    }

    @Test
    fun `program lookup returns typed found and missing results`() {
        val program = audioProgram {
            control("intensity", 0.4f, 0f..1f)
            sfx("place") { oscillator(OscillatorShape.SINE) }
        }

        val found = assertIs<AudioLookupResult.Found<SoundEffectDeclaration>>(program.sfx(SfxName("place")))
        val missing = assertIs<AudioLookupResult.Missing>(program.sfx(SfxName("missing")))

        assertEquals(SfxName("place"), found.value.name)
        assertEquals("sfx[missing]", missing.path)
        assertIs<AudioLookupResult.Found<AudioControlDeclaration>>(program.control(AudioControlName("intensity")))
    }
}
