package ge.yet.game.fruitmerge.audio

import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.store.FruitMergeStore
import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName

internal class FruitMergeAudioAdapter(
    private val audio: MiniAppAudio,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        consume(audio.playMusic(FruitMergeAudio.program))
    }

    fun play(label: FruitMergeStore.Label) {
        val name = when (label) {
            FruitMergeStore.Label.DropAccepted -> FruitMergeAudio.Drop
            is FruitMergeStore.Label.MergeResolved -> mergeSfx(label.level)
            FruitMergeStore.Label.ClearApplied -> FruitMergeAudio.Clear
            FruitMergeStore.Label.ShakeApplied -> FruitMergeAudio.Shake
            FruitMergeStore.Label.ResultReached -> FruitMergeAudio.GameOver
        }
        playSfx(name)
    }

    private fun mergeSfx(level: FruitLevel): SfxName = when (level) {
        FruitLevel.BLUEBERRY,
        FruitLevel.CHERRY,
        FruitLevel.STRAWBERRY,
        -> FruitMergeAudio.MergeLow
        FruitLevel.PLUM,
        FruitLevel.MANDARIN,
        FruitLevel.APPLE,
        FruitLevel.PEAR,
        -> FruitMergeAudio.MergeMid
        FruitLevel.PEACH,
        FruitLevel.PINEAPPLE,
        FruitLevel.MELON,
        -> FruitMergeAudio.MergeHigh
    }

    private fun playSfx(name: SfxName) {
        consume(audio.playSfx(FruitMergeAudio.program, name))
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
