package ge.yet.sample.counter

import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CounterComponentTest {
    @Test
    fun `counter starts at zero and increments from current model`() {
        val setup = createComponent()

        assertEquals(CounterComponent.Model(count = 0), setup.component.model.value)

        setup.component.onIncrementClicked()
        setup.component.onIncrementClicked()

        assertEquals(2, setup.component.model.value.count)
    }

    @Test
    fun `component exposes the session visibility source`() {
        val setup = createComponent()

        assertEquals(MiniAppVisibility.ACTIVE, setup.component.visibility.value)
        setup.visibility.set(MiniAppVisibility.OBSCURED)
        assertEquals(MiniAppVisibility.OBSCURED, setup.component.visibility.value)
        setup.visibility.set(MiniAppVisibility.BACKGROUND)
        assertEquals(MiniAppVisibility.BACKGROUND, setup.component.visibility.value)
    }

    @Test
    fun `resumed component records lifecycle destruction exactly once`() {
        val setup = createComponent()

        setup.lifecycle.resume()
        setup.lifecycle.stop()
        setup.lifecycle.destroy()
        setup.lifecycle.destroy()

        assertEquals(1, setup.component.destroyCount)
    }

    @Test
    fun `counter audio demonstrates an ocean program and four reusable effects`() {
        val setup = createComponent()

        setup.component.onPlayMusicClicked()
        CounterComponent.SoundEffect.entries.forEach(setup.component::onSoundEffectClicked)

        val program = setup.audio.musicPrograms.single()
        assertEquals(listOf("intensity"), program.controls.map { it.name.value })
        assertEquals(4, program.musicTracks.size)
        assertEquals(
            listOf("placement_click", "success_sweep", "explosion", "power_up"),
            setup.audio.sfxNames.map(SfxName::value),
        )
        assertTrue(setup.component.model.value.musicPlaying)
    }

    @Test
    fun `counter forwards stop and accepted intensity changes to session audio`() {
        val setup = createComponent()

        setup.component.onPlayMusicClicked()
        setup.component.onIntensityChanged(0.8f)
        setup.component.onStopMusicClicked()

        assertEquals(listOf(AudioControlName("intensity") to 0.8f), setup.audio.controls)
        assertEquals(1, setup.audio.stopCount)
        assertEquals(0.8f, setup.component.model.value.intensity)
        assertEquals(false, setup.component.model.value.musicPlaying)
    }

    @Test
    fun `rejected playback never creates a false playing state`() {
        val setup = createComponent()
        setup.audio.musicResult =
            AudioCommandResult.Rejected(AudioCommandRejection.PLAYBACK_SUPPRESSED)

        setup.component.onPlayMusicClicked()

        assertEquals(false, setup.component.model.value.musicPlaying)
        assertEquals(setup.audio.musicResult, setup.component.model.value.lastAudioResult)
    }

    private fun createComponent(): Setup {
        val lifecycle = MiniAppLifecycleHarness()
        val visibility = MutableMiniAppVisibilitySource()
        val audio = RecordingMiniAppAudio()
        return Setup(
            component = DefaultCounterComponent(
                componentContext = lifecycle.componentContext,
                visibilitySource = visibility,
                audio = audio,
            ),
            lifecycle = lifecycle,
            visibility = visibility,
            audio = audio,
        )
    }

    private data class Setup(
        val component: DefaultCounterComponent,
        val lifecycle: MiniAppLifecycleHarness,
        val visibility: MutableMiniAppVisibilitySource,
        val audio: RecordingMiniAppAudio,
    )

    private class RecordingMiniAppAudio : MiniAppAudio {
        val musicPrograms = mutableListOf<AudioProgram>()
        val sfxNames = mutableListOf<SfxName>()
        val controls = mutableListOf<Pair<AudioControlName, Float>>()
        var stopCount = 0
        var musicResult: AudioCommandResult = AudioCommandResult.Accepted

        override fun playMusic(program: AudioProgram): AudioCommandResult =
            musicResult.also { musicPrograms += program }

        override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult =
            AudioCommandResult.Accepted.also { stopCount += 1 }

        override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult =
            AudioCommandResult.Accepted.also { sfxNames += name }

        override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
            AudioCommandResult.Accepted.also { controls += name to value }
    }
}
