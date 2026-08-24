package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.NoiseColor
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms
import ge.yet.game.pattern.degrade
import ge.yet.game.pattern.sequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object MenuAudio {
    val Intensity = AudioControlName("intensity")
    val Placement = SfxName("placement")

    val program = audioProgram {
        tempo(84f)
        val intensity = control("intensity", default = 0.35f, range = 0f..1f)
        include(
            OceanBreeze(
                name = "menu_ocean",
                seed = 2_026_08_23L,
                gain = 0.42f,
                density = 0.12f,
                stereo = 0.75f,
                wind = intensity.map(0.15f, 0.85f),
                water = intensity.map(0.25f, 0.75f),
                waves = intensity.map(0.1f, 0.7f),
                chimes = intensity.map(0.02f, 0.35f),
            ),
        )
        include(PlacementClick(name = Placement.value, gain = 0.3f))
    }
}

private val deterministicMelody = sequence(
    listOf(60, 64, 67, 72).map { AudioNote.Pitched(MidiNote.of(it)) },
).degrade(probability = 0.25f, seed = 42L)

private val retroProgram = audioProgram {
    instrument("retro_lead") {
        oscillator(OscillatorShape.PULSE, gain = 0.38f)
        envelope(attack = 2.ms, decay = 40.ms, sustain = 0.65f, release = 80.ms)
        bitCrush(bitDepth = 8, sampleRateReduction = 4)
    }
    musicTrack("retro_theme") {
        instrument("retro_lead")
        notes(deterministicMelody)
        gain(0.32f)
    }
}

private val originalCollisionProgram = audioProgram {
    sfx("collision") {
        noise(NoiseColor.BROWN, gain = 0.32f, seed = 17L)
        oscillator(OscillatorShape.SINE, gain = 0.16f)
        pitch(from = 110.hz, to = 48.hz, duration = 180.ms)
        envelope(attack = 1.ms, decay = 50.ms, sustain = 0.2f, release = 220.ms)
        lowPass(cutoff = 900.hz, resonance = 0.2f)
        distortion(amount = 0.12f)
    }
}

private class GameAudio(private val audio: MiniAppAudio) {
    fun start() = audio.playMusic(MenuAudio.program)

    fun setIntensity(value: Float) = audio.setControl(MenuAudio.Intensity, value)

    fun placement() = audio.playSfx(MenuAudio.program, MenuAudio.Placement)

    fun pause() = audio.stopMusic(fadeOut = 120.ms)
}

class AuthorDocumentationSnippetTest {
    @Test
    fun `author guide programs use valid public declarations`() {
        assertEquals(1, MenuAudio.program.controls.size)
        assertEquals(1, MenuAudio.program.soundEffects.size)
        assertTrue(MenuAudio.program.musicTracks.isNotEmpty())
        assertEquals(1, retroProgram.musicTracks.size)
        assertEquals(1, originalCollisionProgram.soundEffects.size)
    }

    @Test
    fun `author guide runtime wrapper uses only MiniAppAudio commands`() {
        val recording = RecordingAudio()
        val gameAudio = GameAudio(recording)

        gameAudio.start()
        gameAudio.setIntensity(0.8f)
        gameAudio.placement()
        gameAudio.pause()

        assertEquals(listOf("music", "control:intensity=0.8", "sfx:placement", "stop:120"), recording.commands)
    }
}

private class RecordingAudio : MiniAppAudio {
    val commands = mutableListOf<String>()

    override fun playMusic(program: AudioProgram): AudioCommandResult = accepted("music")

    override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult =
        accepted("stop:${(fadeOut.seconds * 1_000).toInt()}")

    override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult = accepted("sfx:${name.value}")

    override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
        accepted("control:${name.value}=$value")

    private fun accepted(command: String): AudioCommandResult {
        commands += command
        return AudioCommandResult.Accepted
    }
}
