package ge.yet.game.twentyfortyeight.audio

import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDiagnostic
import ge.yet.game.miniapp.audio.AudioDiagnosticCode
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.twentyfortyeight.engine.AudioControls
import ge.yet.game.twentyfortyeight.engine.TileValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TwentyFortyEightAudioAdapterTest {
    @Test
    fun `start plays the game program exactly once when accepted`() {
        val audio = RecordingMiniAppAudio()
        val adapter = TwentyFortyEightAudioAdapter(audio)

        adapter.start()
        adapter.start()

        assertEquals(listOf<Command>(Command.PlayMusic(TwentyFortyEightAudio.program)), audio.commands)
    }

    @Test
    fun `start consumes rejection once without immediate retry`() {
        val rejected = rejected(AudioCommandRejection.BACKEND_UNAVAILABLE)
        val audio = RecordingMiniAppAudio(listOf(rejected))
        val adapter = TwentyFortyEightAudioAdapter(audio)

        adapter.start()
        adapter.start()

        assertEquals(listOf<Command>(Command.PlayMusic(TwentyFortyEightAudio.program)), audio.commands)
        assertEquals(listOf<AudioCommandResult>(rejected), audio.returnedResults)
    }

    @Test
    fun `controls start music first and then use exact typed names values in one ordered batch`() {
        val audio = RecordingMiniAppAudio(rejectControlsUntilMusic = true)
        val adapter = TwentyFortyEightAudioAdapter(audio)

        adapter.updateControls(AudioControls(progress = 0.25f, danger = 0.5f, momentum = 0.75f))

        assertEquals(
            listOf<Command>(
                Command.PlayMusic(TwentyFortyEightAudio.program),
                Command.SetControl(TwentyFortyEightAudio.Progress, 0.25f),
                Command.SetControl(TwentyFortyEightAudio.Danger, 0.5f),
                Command.SetControl(TwentyFortyEightAudio.Momentum, 0.75f),
            ),
            audio.commands,
        )
        assertEquals(1, audio.controlBatchCount)
    }

    @Test
    fun `identical accepted controls are attempted at most once`() {
        val audio = RecordingMiniAppAudio()
        val adapter = TwentyFortyEightAudioAdapter(audio)
        val controls = AudioControls(progress = 0.25f, danger = 0.5f, momentum = 0f)

        adapter.updateControls(controls)
        adapter.updateControls(controls)

        assertEquals(1, audio.controlBatchCount)
    }

    @Test
    fun `identical rejected controls wait for a meaningfully changed tuple`() {
        val audio = RecordingMiniAppAudio(
            listOf(
                AudioCommandResult.Accepted,
                rejected(AudioCommandRejection.COMMAND_QUEUE_FULL),
                AudioCommandResult.Accepted,
                AudioCommandResult.Accepted,
            ),
        )
        val adapter = TwentyFortyEightAudioAdapter(audio)
        val first = AudioControls(progress = 0.25f, danger = 0.5f, momentum = 0f)

        adapter.updateControls(first)
        adapter.updateControls(first)
        adapter.updateControls(first.copy(progress = 0.28125f))

        assertEquals(2, audio.controlBatchCount)
        assertEquals(
            listOf(0.25f, 0.5f, 0f, 0.28125f, 0.5f, 0f),
            audio.commands.filterIsInstance<Command.SetControl>().map(Command.SetControl::value),
        )
    }

    @Test
    fun `second or third control rejection deduplicates until a changed full batch`() {
        listOf(1, 2).forEach { rejectedControlIndex ->
            val firstBatchResults = List(CONTROL_COUNT) { index ->
                if (index == rejectedControlIndex) {
                    rejected(AudioCommandRejection.COMMAND_QUEUE_FULL)
                } else {
                    AudioCommandResult.Accepted
                }
            }
            val audio = RecordingMiniAppAudio(
                queuedResults = listOf(AudioCommandResult.Accepted) + firstBatchResults,
            )
            val adapter = TwentyFortyEightAudioAdapter(audio)
            val first = AudioControls(progress = 0.25f, danger = 0.5f, momentum = 0f)

            adapter.updateControls(first)
            adapter.updateControls(first)
            adapter.updateControls(first.copy(momentum = 0.03125f))

            assertEquals(2, audio.controlBatchCount, "rejectedControlIndex=$rejectedControlIndex")
            assertEquals(
                listOf(
                    TwentyFortyEightAudio.Progress,
                    TwentyFortyEightAudio.Danger,
                    TwentyFortyEightAudio.Momentum,
                    TwentyFortyEightAudio.Progress,
                    TwentyFortyEightAudio.Danger,
                    TwentyFortyEightAudio.Momentum,
                ),
                audio.commands.filterIsInstance<Command.SetControl>().map(Command.SetControl::name),
            )
            assertEquals(
                listOf(0.25f, 0.5f, 0f, 0.25f, 0.5f, 0.03125f),
                audio.commands.filterIsInstance<Command.SetControl>().map(Command.SetControl::value),
            )
        }
    }

    @Test
    fun `every accepted and rejected result branch is safely consumed once`() {
        val results = listOf<AudioCommandResult>(AudioCommandResult.Accepted) +
            AudioCommandRejection.entries.map(::rejected)

        results.forEach { result ->
            val audio = RecordingMiniAppAudio(listOf(result))

            TwentyFortyEightAudioAdapter(audio).play(AudioEvent.TileSpawn)

            assertEquals(
                listOf<Command>(
                    Command.PlaySfx(TwentyFortyEightAudio.program, TwentyFortyEightAudio.TileSpawn),
                ),
                audio.commands,
            )
            assertEquals(listOf(result), audio.returnedResults)
        }
    }

    @Test
    fun `direct events map to one exact SFX each`() {
        val audio = RecordingMiniAppAudio()
        val adapter = TwentyFortyEightAudioAdapter(audio)

        listOf(
            AudioEvent.TileSpawn,
            AudioEvent.Move,
            AudioEvent.Undo,
            AudioEvent.NewBest,
            AudioEvent.Victory,
            AudioEvent.GameOver,
        ).forEach(adapter::play)

        assertEquals(
            listOf("tile_spawn", "move", "undo", "new_best", "victory", "game_over"),
            audio.sfxNames,
        )
    }

    @Test
    fun `changed move emits move spawn and only its highest merge tier in order`() {
        val audio = RecordingMiniAppAudio()
        val adapter = TwentyFortyEightAudioAdapter(audio)

        adapter.play(
            AudioEvent.MoveResolved(
                spawned = true,
                mergeValues = listOf(
                    TileValue(8L),
                    TileValue(128L),
                    TileValue(2_048L),
                    TileValue(64L),
                ),
            ),
        )

        assertEquals(listOf("move", "tile_spawn", "merge_high"), audio.sfxNames)
    }

    @Test
    fun `move without spawn omits spawn but retains move before merge`() {
        val audio = RecordingMiniAppAudio()

        TwentyFortyEightAudioAdapter(audio).play(
            AudioEvent.MoveResolved(spawned = false, mergeValues = listOf(TileValue(64L))),
        )

        assertEquals(listOf("move", "merge_mid"), audio.sfxNames)
    }

    @Test
    fun `merge tiers honor exact boundaries and empty merges stay silent after move`() {
        val cases = listOf(
            emptyList<TileValue>() to null,
            listOf(TileValue(2L)) to null,
            listOf(TileValue(4L)) to "merge_low",
            listOf(TileValue(32L)) to "merge_low",
            listOf(TileValue(64L)) to "merge_mid",
            listOf(TileValue(512L)) to "merge_mid",
            listOf(TileValue(1_024L)) to "merge_high",
            listOf(TileValue(TileValue.MAX_VALUE)) to "merge_high",
        )

        cases.forEach { (mergeValues, expectedMerge) ->
            val audio = RecordingMiniAppAudio()

            TwentyFortyEightAudioAdapter(audio).play(
                AudioEvent.MoveResolved(spawned = false, mergeValues = mergeValues),
            )

            assertEquals(listOfNotNull("move", expectedMerge), audio.sfxNames)
        }
    }

    @Test
    fun `merge event accepts only valid tile values by construction`() {
        listOf(
            0L,
            -2L,
            3L,
            33L,
            TileValue.MAX_VALUE + 1L,
            Long.MAX_VALUE,
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>("invalid=$invalid") {
                TileValue(invalid)
            }
        }
    }

    @Test
    fun `adapter constructor owns only audio and real behavior never stops music`() {
        val audio = RecordingMiniAppAudio(listOf(rejected(AudioCommandRejection.PLAYBACK_SUPPRESSED)))
        val factory: (MiniAppAudio) -> TwentyFortyEightAudioAdapter = ::TwentyFortyEightAudioAdapter

        factory(audio).play(AudioEvent.Move)

        assertEquals(1, audio.commands.size)
        assertEquals(listOf("move"), audio.sfxNames)
        assertTrue(audio.commands.none { it is Command.StopMusic })
    }

    private sealed interface Command {
        data class PlayMusic(val program: AudioProgram) : Command
        data class StopMusic(val fadeOut: AudioDuration) : Command
        data class PlaySfx(val program: AudioProgram, val name: SfxName) : Command
        data class SetControl(val name: AudioControlName, val value: Float) : Command
    }

    private class RecordingMiniAppAudio(
        private val queuedResults: List<AudioCommandResult> = emptyList(),
        private val rejectControlsUntilMusic: Boolean = false,
    ) : MiniAppAudio {
        val commands = mutableListOf<Command>()
        val returnedResults = mutableListOf<AudioCommandResult>()
        private var resultIndex = 0
        private var musicAttempted = false

        val controlBatchCount: Int
            get() = commands.filterIsInstance<Command.SetControl>().size / CONTROL_COUNT

        val sfxNames: List<String>
            get() = commands.filterIsInstance<Command.PlaySfx>().map { it.name.value }

        override fun playMusic(program: AudioProgram): AudioCommandResult =
            record(Command.PlayMusic(program))

        override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult =
            record(Command.StopMusic(fadeOut))

        override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult =
            record(Command.PlaySfx(program, name))

        override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
            record(Command.SetControl(name, value))

        private fun record(command: Command): AudioCommandResult {
            commands += command
            val result = if (
                rejectControlsUntilMusic &&
                command is Command.SetControl &&
                !musicAttempted
            ) {
                rejected(AudioCommandRejection.UNKNOWN_CONTROL)
            } else {
                queuedResults.getOrNull(resultIndex) ?: AudioCommandResult.Accepted
            }
            if (command is Command.PlayMusic) musicAttempted = true
            resultIndex += 1
            returnedResults += result
            return result
        }
    }

    private companion object {
        const val CONTROL_COUNT = 3

        fun rejected(reason: AudioCommandRejection): AudioCommandResult.Rejected =
            AudioCommandResult.Rejected(
                reason = reason,
                diagnostics = listOf(
                    AudioDiagnostic(
                        code = AudioDiagnosticCode.UNRESOLVED_CONTROL,
                        path = "private.runtime.path",
                        message = "free-form host diagnostic must stay inside audio",
                    ),
                ),
            )
    }
}
