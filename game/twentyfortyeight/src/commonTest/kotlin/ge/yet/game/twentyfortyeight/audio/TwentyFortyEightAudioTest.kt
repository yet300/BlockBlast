package ge.yet.game.twentyfortyeight.audio

import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.AudioParameter
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.pattern.CycleTime
import ge.yet.game.pattern.TimeArc
import ge.yet.game.pattern.degrade
import ge.yet.game.pattern.query
import ge.yet.game.pattern.sequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TwentyFortyEightAudioTest {
    @Test
    fun `program exposes only the approved controls and SFX`() {
        val program = TwentyFortyEightAudio.program

        assertEquals(
            listOf("progress", "danger", "momentum"),
            program.controls.map { it.name.value },
        )
        assertEquals(
            setOf(
                "tile_spawn",
                "move",
                "merge_low",
                "merge_mid",
                "merge_high",
                "undo",
                "new_best",
                "victory",
                "game_over",
            ),
            program.soundEffects.mapTo(linkedSetOf()) { it.name.value },
        )
        assertEquals(3, program.controls.map { it.name }.distinct().size)
        assertEquals(9, program.soundEffects.map { it.name }.distinct().size)
        assertEquals(
            listOf(
                Triple("progress", 0f, 0f..1f),
                Triple("danger", 0.075f, 0f..1f),
                Triple("momentum", 0f, 0f..1f),
            ),
            program.controls.map { Triple(it.name.value, it.default, it.range) },
        )
        assertEquals(
            listOf("progress", "danger", "momentum"),
            listOf(
                TwentyFortyEightAudio.Progress.value,
                TwentyFortyEightAudio.Danger.value,
                TwentyFortyEightAudio.Momentum.value,
            ),
        )
        assertEquals(
            setOf(
                TwentyFortyEightAudio.TileSpawn,
                TwentyFortyEightAudio.Move,
                TwentyFortyEightAudio.MergeLow,
                TwentyFortyEightAudio.MergeMid,
                TwentyFortyEightAudio.MergeHigh,
                TwentyFortyEightAudio.Undo,
                TwentyFortyEightAudio.NewBest,
                TwentyFortyEightAudio.Victory,
                TwentyFortyEightAudio.GameOver,
            ),
            program.soundEffects.mapTo(linkedSetOf()) { it.name },
        )
    }

    @Test
    fun `program keeps the approved original patterns and deterministic seeds`() {
        val program = TwentyFortyEightAudio.program
        assertEquals(76f, program.tempo.bpm)
        assertEquals(listOf("warm_pad", "soft_pulse", "glass_accents"), program.musicTracks.map { it.name.value })

        assertEquals(
            listOf(45, 52, 57, 50, 55, 62, 48, 55),
            pitchedNotes("warm_pad"),
        )
        assertEquals(
            listOf(33, 33, 40, 36, 43, 36, 38, 45),
            pitchedNotes("soft_pulse"),
        )

        val warmPan = assertIs<AudioParameter.SmoothNoise>(
            program.musicTracks.single { it.name.value == "warm_pad" }.pan,
        )
        val glassPan = assertIs<AudioParameter.SmoothNoise>(
            program.musicTracks.single { it.name.value == "glass_accents" }.pan,
        )
        assertEquals(2_048_101L, warmPan.seed)
        assertEquals(2_048_203L, glassPan.seed)

        val expectedGlassPattern = sequence(
            listOf(69, 76, 72, 81, 74, 67, 79, 71).map {
                AudioNote.Pitched(MidiNote.of(it))
            },
        ).degrade(probability = 0.62f, seed = 2_048_202L)
        val actualGlassPattern = program.musicTracks.single { it.name.value == "glass_accents" }.pattern
        val verificationArcs = listOf(
            TimeArc(CycleTime.ZERO, CycleTime.of(4)),
            TimeArc(CycleTime.of(1, 2), CycleTime.of(5, 2)),
            TimeArc(CycleTime.of(3), CycleTime.of(7)),
        )
        verificationArcs.forEach { arc ->
            assertEquals(expectedGlassPattern.query(arc), actualGlassPattern.query(arc), arc.toString())
        }

        assertEquals(
            mapOf(
                "tile_spawn" to listOf(7L),
                "move" to listOf(7L),
                "undo" to listOf(2_048_301L),
            ),
            program.soundEffects
                .filter { it.noises.isNotEmpty() }
                .associate { effect -> effect.name.value to effect.noises.map { it.seed } },
        )
    }

    private fun pitchedNotes(trackName: String): List<Int> =
        TwentyFortyEightAudio.program.musicTracks.single { it.name.value == trackName }
            .pattern.query(TimeArc.unit)
            .map { event -> assertIs<AudioNote.Pitched>(event.value).midi.value }
}
