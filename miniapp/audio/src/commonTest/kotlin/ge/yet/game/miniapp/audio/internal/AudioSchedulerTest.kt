package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.compile
import ge.yet.game.pattern.sequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class AudioSchedulerTest {
    @Test
    fun `scheduler reuses one bounded event buffer across realtime blocks`() {
        val scheduler = scheduler(
            notes = listOf(60, 61, 62, 63),
            sampleRate = 8_000,
            tempo = 240f,
        )

        val first = scheduler.scheduleBlockInto(startFrame = 0, frameCount = 2_000)
        val second = scheduler.scheduleBlockInto(startFrame = 2_000, frameCount = 2_000)

        assertSame(first, second)
        assertEquals(listOf(61), second.map { it.note.value })
    }

    @Test
    fun `sequence boundaries map to exact sample offsets`() {
        val scheduler = scheduler(
            notes = listOf(60, 61, 62, 63),
            sampleRate = 8_000,
            tempo = 240f,
        )

        val events = scheduler.scheduleBlock(startFrame = 0, frameCount = 8_000)

        assertEquals(listOf(0, 2_000, 4_000, 6_000), events.map { it.frameOffset })
        assertEquals(listOf(2_000L, 2_000L, 2_000L, 2_000L), events.map { it.durationFrames })
        assertEquals(listOf(60, 61, 62, 63), events.map { it.note.value })
    }

    @Test
    fun `half open adjacent blocks neither duplicate nor lose boundary events`() {
        val scheduler = scheduler(
            notes = listOf(60, 61, 62, 63),
            sampleRate = 8_000,
            tempo = 240f,
        )

        val first = scheduler.scheduleBlock(startFrame = 0, frameCount = 4_000)
        val second = scheduler.scheduleBlock(startFrame = 4_000, frameCount = 4_000)

        assertEquals(listOf(60, 61), first.map { it.note.value })
        assertEquals(listOf(62, 63), second.map { it.note.value })
        assertEquals(listOf(0, 2_000), second.map { it.frameOffset })
    }

    @Test
    fun `multi cycle block queries each occurrence exactly once`() {
        val scheduler = scheduler(
            notes = listOf(60),
            sampleRate = 8_000,
            tempo = 240f,
        )

        val events = scheduler.scheduleBlock(startFrame = 0, frameCount = 16_000)

        assertEquals(listOf(0L, 8_000L), events.map { it.absoluteStartFrame })
        assertEquals(listOf(8_000L, 8_000L), events.map { it.durationFrames })
    }

    @Test
    fun `nonzero block reports event offset relative to that block`() {
        val scheduler = scheduler(
            notes = listOf(60, 61, 62, 63),
            sampleRate = 8_000,
            tempo = 240f,
        )

        val events = scheduler.scheduleBlock(startFrame = 3_500, frameCount = 2_000)

        assertEquals(1, events.size)
        assertEquals(62, events.single().note.value)
        assertEquals(500, events.single().frameOffset)
        assertEquals(4_000L, events.single().absoluteStartFrame)
    }

    @Test
    fun `event rounded onto a block boundary is owned by the following block`() {
        val scheduler = scheduler(
            notes = listOf(60, 61, 62),
            sampleRate = 8_000,
            tempo = 240f,
        )

        val beforeBoundary = scheduler.scheduleBlock(startFrame = 0, frameCount = 2_667)
        val fromBoundary = scheduler.scheduleBlock(startFrame = 2_667, frameCount = 2_666)

        assertEquals(listOf(60), beforeBoundary.map { it.note.value })
        assertEquals(listOf(61), fromBoundary.map { it.note.value })
        assertEquals(0, fromBoundary.single().frameOffset)
    }

    @Test
    fun `track declaration order breaks equal time ties deterministically`() {
        val program = audioProgram {
            tempo(240f)
            instrument("lead") { oscillator(OscillatorShape.SINE) }
            musicTrack("first") {
                instrument("lead")
                notes(MidiNote.of(60))
            }
            musicTrack("second") {
                instrument("lead")
                notes(MidiNote.of(72))
            }
        }
        val compiled = assertIs<AudioCompilationResult.Success>(program.compile()).program

        val events = AudioScheduler(compiled, sampleRate = 8_000).scheduleBlock(0, 1_000)

        assertEquals(listOf(0, 1), events.map { it.trackIndex })
        assertEquals(listOf(60, 72), events.map { it.note.value })
    }

    private fun scheduler(notes: List<Int>, sampleRate: Int, tempo: Float): AudioScheduler {
        val program = audioProgram {
            tempo(tempo)
            instrument("lead") { oscillator(OscillatorShape.SINE) }
            musicTrack("notes") {
                instrument("lead")
                notes(sequence(notes.map { AudioNote.Pitched(MidiNote.of(it)) }))
            }
        }
        val compiled = assertIs<AudioCompilationResult.Success>(program.compile()).program
        return AudioScheduler(compiled, sampleRate)
    }
}
