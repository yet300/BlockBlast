package ge.yet.game.twentyfortyeight.audio

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms
import ge.yet.game.miniapp.audio.smoothNoise
import ge.yet.game.miniapp.audio.times
import ge.yet.game.miniapp.audio.presets.AnalogBass
import ge.yet.game.miniapp.audio.presets.GlassBell
import ge.yet.game.miniapp.audio.presets.PlacementClick
import ge.yet.game.miniapp.audio.presets.PowerUp
import ge.yet.game.miniapp.audio.presets.SoftPad
import ge.yet.game.miniapp.audio.presets.SuccessSweep
import ge.yet.game.pattern.degrade
import ge.yet.game.pattern.sequence

internal object TwentyFortyEightAudio {
    val Progress = AudioControlName("progress")
    val Danger = AudioControlName("danger")
    val Momentum = AudioControlName("momentum")

    val TileSpawn = SfxName("tile_spawn")
    val Move = SfxName("move")
    val MergeLow = SfxName("merge_low")
    val MergeMid = SfxName("merge_mid")
    val MergeHigh = SfxName("merge_high")
    val Undo = SfxName("undo")
    val NewBest = SfxName("new_best")
    val Victory = SfxName("victory")
    val GameOver = SfxName("game_over")

    val program = audioProgram {
        tempo(76f)
        val progress = control(Progress.value, 0f, 0f..1f)
        val danger = control(Danger.value, 0.075f, 0f..1f)
        val momentum = control(Momentum.value, 0f, 0f..1f)

        include(SoftPad(name = "warm_pad_voice", gain = 0.42f))
        include(AnalogBass(name = "soft_pulse_voice", gain = 0.28f))
        include(GlassBell(name = "glass_accent_voice", gain = 0.24f))
        include(PlacementClick(name = TileSpawn.value, gain = 0.16f))
        include(PlacementClick(name = Move.value, gain = 0.10f))
        include(PowerUp(name = NewBest.value, gain = 0.28f))
        include(SuccessSweep(name = Victory.value, gain = 0.34f))

        musicTrack("warm_pad") {
            instrument("warm_pad_voice")
            notes(listOf(45, 52, 57, 50, 55, 62, 48, 55).map(MidiNote::of))
            gain(progress.map(0.12f, 0.24f))
            pan(smoothNoise(2_048_101L, 0.31.hz, -0.46f..0.46f))
            reverb(send = 0.18f)
        }
        musicTrack("soft_pulse") {
            instrument("soft_pulse_voice")
            notes(
                sequence(
                    listOf(33, 33, 40, 36, 43, 36, 38, 45).map {
                        AudioNote.Pitched(MidiNote.of(it))
                    },
                ),
            )
            gain(progress.map(0.02f, 0.13f) * momentum.map(0.35f, 1f))
            pan(-0.08f)
        }
        musicTrack("glass_accents") {
            instrument("glass_accent_voice")
            notes(
                sequence(
                    listOf(69, 76, 72, 81, 74, 67, 79, 71).map {
                        AudioNote.Pitched(MidiNote.of(it))
                    },
                ).degrade(probability = 0.62f, seed = 2_048_202L),
            )
            gain(danger.map(0.025f, 0.14f))
            pan(smoothNoise(2_048_203L, 0.73.hz, -0.72f..0.72f))
            delay(time = 170.ms, feedback = 0.18f)
        }
        include(TwentyFortyEightSfx.fragment)
    }
}
