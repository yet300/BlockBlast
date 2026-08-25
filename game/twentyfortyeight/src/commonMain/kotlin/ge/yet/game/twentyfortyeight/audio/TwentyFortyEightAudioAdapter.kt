package ge.yet.game.twentyfortyeight.audio

import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.twentyfortyeight.engine.AudioControls
import ge.yet.game.twentyfortyeight.engine.TileValue

internal sealed interface AudioEvent {
    data object TileSpawn : AudioEvent
    data object Move : AudioEvent

    data class MoveResolved(
        val spawned: Boolean,
        val mergeValues: List<TileValue>,
    ) : AudioEvent

    data object Undo : AudioEvent
    data object NewBest : AudioEvent
    data object Victory : AudioEvent
    data object GameOver : AudioEvent
}

internal class TwentyFortyEightAudioAdapter(
    private val audio: MiniAppAudio,
) {
    private var started = false
    private var lastAttemptedControls: AudioControls? = null

    fun start() {
        if (started) return
        started = true
        consume(audio.playMusic(TwentyFortyEightAudio.program))
    }

    fun updateControls(controls: AudioControls) {
        start()
        if (controls == lastAttemptedControls) return
        lastAttemptedControls = controls

        consume(audio.setControl(TwentyFortyEightAudio.Progress, controls.progress))
        consume(audio.setControl(TwentyFortyEightAudio.Danger, controls.danger))
        consume(audio.setControl(TwentyFortyEightAudio.Momentum, controls.momentum))
    }

    fun play(event: AudioEvent) {
        when (event) {
            AudioEvent.TileSpawn -> playSfx(TwentyFortyEightAudio.TileSpawn)
            AudioEvent.Move -> playSfx(TwentyFortyEightAudio.Move)
            is AudioEvent.MoveResolved -> playMove(event)
            AudioEvent.Undo -> playSfx(TwentyFortyEightAudio.Undo)
            AudioEvent.NewBest -> playSfx(TwentyFortyEightAudio.NewBest)
            AudioEvent.Victory -> playSfx(TwentyFortyEightAudio.Victory)
            AudioEvent.GameOver -> playSfx(TwentyFortyEightAudio.GameOver)
        }
    }

    private fun playMove(event: AudioEvent.MoveResolved) {
        playSfx(TwentyFortyEightAudio.Move)
        if (event.spawned) playSfx(TwentyFortyEightAudio.TileSpawn)

        val mergeSfx = when (event.mergeValues.maxOfOrNull(TileValue::value)) {
            in 4L..32L -> TwentyFortyEightAudio.MergeLow
            in 64L..512L -> TwentyFortyEightAudio.MergeMid
            in 1_024L..TileValue.MAX_VALUE -> TwentyFortyEightAudio.MergeHigh
            else -> null
        }
        if (mergeSfx != null) playSfx(mergeSfx)
    }

    private fun playSfx(name: SfxName) {
        consume(audio.playSfx(TwentyFortyEightAudio.program, name))
    }

    private fun consume(result: AudioCommandResult) {
        when (result) {
            AudioCommandResult.Accepted -> Unit
            is AudioCommandResult.Rejected -> when (result.reason) {
                AudioCommandRejection.INVALID_PROGRAM -> Unit
                AudioCommandRejection.UNKNOWN_SFX -> Unit
                AudioCommandRejection.UNKNOWN_CONTROL -> Unit
                AudioCommandRejection.CONTROL_OUT_OF_RANGE -> Unit
                AudioCommandRejection.PLAYBACK_SUPPRESSED -> Unit
                AudioCommandRejection.SESSION_CLOSED -> Unit
                AudioCommandRejection.COMMAND_QUEUE_FULL -> Unit
                AudioCommandRejection.BACKEND_UNAVAILABLE -> Unit
            }
        }
    }
}
