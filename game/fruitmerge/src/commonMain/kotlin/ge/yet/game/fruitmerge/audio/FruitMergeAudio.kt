package ge.yet.game.fruitmerge.audio

import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.NoiseColor
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms
import ge.yet.game.miniapp.audio.presets.GlassBell
import ge.yet.game.miniapp.audio.presets.PlacementClick
import ge.yet.game.miniapp.audio.presets.PowerUp
import ge.yet.game.miniapp.audio.smoothNoise
import ge.yet.game.pattern.degrade
import ge.yet.game.pattern.sequence

internal object FruitMergeAudio {
    val Drop = SfxName("drop")
    val MergeLow = SfxName("merge_low")
    val MergeMid = SfxName("merge_mid")
    val MergeHigh = SfxName("merge_high")
    val Clear = SfxName("clear")
    val Shake = SfxName("shake")
    val GameOver = SfxName("game_over")

    val program = audioProgram {
        tempo(84f)

        instrument("crate_knock_voice") {
            oscillator(OscillatorShape.SINE, gain = 0.16f)
            noise(NoiseColor.BROWN, gain = 0.045f, seed = 6_170_101L)
            envelope(attack = 1.ms, decay = 34.ms, sustain = 0.08f, release = 72.ms)
            lowPass(cutoff = 820.hz, resonance = 0.22f)
        }
        instrument("fruit_roll_voice") {
            oscillator(OscillatorShape.TRIANGLE, gain = 0.055f)
            noise(NoiseColor.PINK, gain = 0.07f, seed = 6_170_102L)
            envelope(attack = 7.ms, decay = 95.ms, sustain = 0.18f, release = 170.ms)
            lowPass(cutoff = 1_150.hz, resonance = 0.12f)
        }
        include(GlassBell(name = "fruit_glass_voice", gain = 0.17f))

        musicTrack("crate_knocks") {
            instrument("crate_knock_voice")
            notes(
                sequence(
                    listOf(36, 43, 36, 40, 36, 45, 38, 43).map {
                        AudioNote.Pitched(MidiNote.of(it))
                    },
                ),
            )
            gain(0.13f)
            pan(smoothNoise(seed = 6_170_201L, rate = 0.41.hz, range = -0.48f..0.48f))
        }
        musicTrack("fruit_rolls") {
            instrument("fruit_roll_voice")
            notes(
                sequence(
                    listOf(48, 50, 45, 52, 47, 50, 43, 47).map {
                        AudioNote.Pitched(MidiNote.of(it))
                    },
                ).degrade(probability = 0.34f, seed = 6_170_202L),
            )
            gain(0.085f)
            pan(smoothNoise(seed = 6_170_203L, rate = 0.67.hz, range = -0.72f..0.72f))
        }
        musicTrack("glass_sprinkles") {
            instrument("fruit_glass_voice")
            notes(
                sequence(
                    listOf(72, 76, 79, 74, 81, 76, 83, 79).map {
                        AudioNote.Pitched(MidiNote.of(it))
                    },
                ).degrade(probability = 0.73f, seed = 6_170_204L),
            )
            gain(0.055f)
            pan(smoothNoise(seed = 6_170_205L, rate = 0.29.hz, range = -0.8f..0.8f))
            reverb(send = 0.12f)
        }

        include(PlacementClick(name = Drop.value, gain = 0.18f))
        include(PowerUp(name = Clear.value, gain = 0.22f))

        sfx(MergeLow.value) {
            oscillator(OscillatorShape.SINE, gain = 0.16f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.055f, detuneCents = 5f)
            pitch(from = 180.hz, to = 136.hz, duration = 105.ms)
            envelope(attack = 2.ms, decay = 34.ms, sustain = 0.22f, release = 95.ms)
            lowPass(cutoff = 1_150.hz, resonance = 0.14f)
        }
        sfx(MergeMid.value) {
            oscillator(OscillatorShape.SINE, gain = 0.18f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.065f, detuneCents = 7f)
            pitch(from = 270.hz, to = 190.hz, duration = 125.ms)
            envelope(attack = 2.ms, decay = 38.ms, sustain = 0.25f, release = 125.ms)
            lowPass(cutoff = 1_650.hz, resonance = 0.15f)
        }
        sfx(MergeHigh.value) {
            oscillator(OscillatorShape.SINE, gain = 0.18f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.075f, detuneCents = 9f)
            partial(ratio = 2.01f, gain = 0.025f)
            pitch(from = 390.hz, to = 275.hz, duration = 155.ms)
            envelope(attack = 3.ms, decay = 44.ms, sustain = 0.28f, release = 175.ms)
            lowPass(cutoff = 2_200.hz, resonance = 0.16f)
        }
        sfx(Shake.value) {
            noise(NoiseColor.BROWN, gain = 0.17f, seed = 6_170_301L)
            noise(NoiseColor.PINK, gain = 0.08f, seed = 6_170_302L)
            oscillator(OscillatorShape.SINE, gain = 0.05f)
            pitch(from = 118.hz, to = 82.hz, duration = 260.ms)
            envelope(attack = 2.ms, decay = 90.ms, sustain = 0.36f, release = 240.ms)
            lowPass(cutoff = 1_400.hz, resonance = 0.20f)
        }
        sfx(GameOver.value) {
            oscillator(OscillatorShape.SINE, gain = 0.14f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.05f, detuneCents = -8f)
            noise(NoiseColor.BROWN, gain = 0.022f, seed = 6_170_303L)
            pitch(from = 220.hz, to = 92.hz, duration = 360.ms)
            envelope(attack = 4.ms, decay = 110.ms, sustain = 0.35f, release = 320.ms)
            lowPass(cutoff = 900.hz, resonance = 0.12f)
        }
    }
}
