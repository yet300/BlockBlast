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
            is FruitMergeStore.Label.DropReleased -> FruitMergeAudio.Release
            is FruitMergeStore.Label.FruitLanded -> landingSfx(label.level)
            is FruitMergeStore.Label.MergeResolved -> mergeSfx(label.level)
            is FruitMergeStore.Label.ClearApplied -> FruitMergeAudio.ClearSlice
            FruitMergeStore.Label.ShakeStarted -> FruitMergeAudio.ShakeLeft
            is FruitMergeStore.Label.ShakePulse -> if (label.index % 2 == 0) {
                FruitMergeAudio.ShakeRight
            } else {
                FruitMergeAudio.ShakeLeft
            }
            FruitMergeStore.Label.DangerEntered -> FruitMergeAudio.DangerEnter
            FruitMergeStore.Label.ResultReached -> FruitMergeAudio.GameOver
        }
        playSfx(name)
    }

    private fun landingSfx(level: FruitLevel): SfxName = when (level) {
        FruitLevel.BLUEBERRY,
        FruitLevel.RASPBERRY,
        FruitLevel.STRAWBERRY,
        FruitLevel.LIME,
        -> FruitMergeAudio.LandingSmall
        FruitLevel.MANDARIN,
        FruitLevel.APPLE,
        FruitLevel.PEAR,
        -> FruitMergeAudio.LandingMedium
        FruitLevel.PEACH,
        FruitLevel.PINEAPPLE,
        FruitLevel.WATERMELON,
        -> FruitMergeAudio.LandingHeavy
    }

    private fun mergeSfx(level: FruitLevel): SfxName = when (level) {
        FruitLevel.BLUEBERRY,
        FruitLevel.RASPBERRY,
        FruitLevel.STRAWBERRY,
        -> FruitMergeAudio.MergeLow
        FruitLevel.LIME,
        FruitLevel.MANDARIN,
        FruitLevel.APPLE,
        FruitLevel.PEAR,
        -> FruitMergeAudio.MergeMid
        FruitLevel.PEACH,
        FruitLevel.PINEAPPLE,
        FruitLevel.WATERMELON,
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
