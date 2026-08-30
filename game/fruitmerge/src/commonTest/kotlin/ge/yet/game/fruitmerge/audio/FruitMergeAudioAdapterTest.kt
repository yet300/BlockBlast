package ge.yet.game.fruitmerge.audio

import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.store.FruitMergeStore
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import kotlin.test.Test
import kotlin.test.assertEquals

class FruitMergeAudioAdapterTest {
    @Test
    fun `music starts once and committed labels map to exact SFX tiers`() {
        val audio = RecordingAudio()
        val adapter = FruitMergeAudioAdapter(audio)

        adapter.start()
        adapter.start()
        adapter.play(FruitMergeStore.Label.DropAccepted)
        adapter.play(FruitMergeStore.Label.MergeResolved(FruitLevel.BLUEBERRY))
        adapter.play(FruitMergeStore.Label.MergeResolved(FruitLevel.APPLE))
        adapter.play(FruitMergeStore.Label.MergeResolved(FruitLevel.MELON))
        adapter.play(FruitMergeStore.Label.ClearApplied)
        adapter.play(FruitMergeStore.Label.ShakeApplied)
        adapter.play(FruitMergeStore.Label.ResultReached)

        assertEquals(1, audio.musicCalls)
        assertEquals(
            listOf("drop", "merge_low", "merge_mid", "merge_high", "clear", "shake", "game_over"),
            audio.sfxNames,
        )
    }

    private class RecordingAudio : MiniAppAudio {
        var musicCalls: Int = 0
        val sfxNames = mutableListOf<String>()

        override fun playMusic(program: AudioProgram): AudioCommandResult {
            musicCalls += 1
            return AudioCommandResult.Accepted
        }

        override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult = AudioCommandResult.Accepted

        override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult {
            sfxNames += name.value
            return AudioCommandResult.Accepted
        }

        override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
            AudioCommandResult.Accepted
    }
}
