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
import ge.yet.game.miniapp.audio.smoothNoise
import ge.yet.game.pattern.degrade
import ge.yet.game.pattern.sequence

internal object FruitMergeAudio {
    val Release = SfxName("release")
    val LandingSmall = SfxName("landing_small")
    val LandingMedium = SfxName("landing_medium")
    val LandingHeavy = SfxName("landing_heavy")
    val MergeLow = SfxName("merge_low")
    val MergeMid = SfxName("merge_mid")
    val MergeHigh = SfxName("merge_high")
    val ClearSlice = SfxName("clear_slice")
    val ShakeLeft = SfxName("shake_left")
    val ShakeRight = SfxName("shake_right")
    val DangerEnter = SfxName("danger_enter")
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

        sfx(Release.value) {
            oscillator(OscillatorShape.TRIANGLE, gain = 0.035f)
            noise(NoiseColor.PINK, gain = 0.050f, seed = 6_170_310L)
            pitch(from = 520.hz, to = 210.hz, duration = 105.ms)
            envelope(attack = 2.ms, decay = 35.ms, sustain = 0.06f, release = 95.ms)
            bandPass(center = 1_050.hz, resonance = 0.10f)
        }
        sfx(LandingSmall.value) {
            oscillator(OscillatorShape.SINE, gain = 0.105f)
            noise(NoiseColor.BROWN, gain = 0.030f, seed = 6_170_311L)
            pitch(from = 210.hz, to = 138.hz, duration = 90.ms)
            envelope(attack = 1.ms, decay = 44.ms, sustain = 0.10f, release = 105.ms)
            lowPass(cutoff = 980.hz, resonance = 0.12f)
        }
        sfx(LandingMedium.value) {
            oscillator(OscillatorShape.SINE, gain = 0.145f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.028f, detuneCents = -8f)
            noise(NoiseColor.BROWN, gain = 0.044f, seed = 6_170_312L)
            pitch(from = 165.hz, to = 88.hz, duration = 125.ms)
            envelope(attack = 1.ms, decay = 54.ms, sustain = 0.12f, release = 175.ms)
            lowPass(cutoff = 820.hz, resonance = 0.16f)
        }
        sfx(LandingHeavy.value) {
            oscillator(OscillatorShape.SINE, gain = 0.185f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.040f, detuneCents = -12f)
            noise(NoiseColor.BROWN, gain = 0.062f, seed = 6_170_313L)
            pitch(from = 128.hz, to = 58.hz, duration = 170.ms)
            envelope(attack = 1.ms, decay = 68.ms, sustain = 0.15f, release = 230.ms)
            lowPass(cutoff = 690.hz, resonance = 0.18f)
        }
        sfx(ClearSlice.value) {
            oscillator(OscillatorShape.SINE, gain = 0.055f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.032f, detuneCents = 14f)
            noise(NoiseColor.WHITE, gain = 0.100f, seed = 6_170_314L)
            noise(NoiseColor.PINK, gain = 0.028f, seed = 6_170_315L)
            pitch(from = 1_480.hz, to = 360.hz, duration = 105.ms)
            envelope(attack = 1.ms, decay = 42.ms, sustain = 0.045f, release = 135.ms)
            bandPass(center = 2_100.hz, resonance = 0.18f)
        }

        sfx(MergeLow.value) {
            oscillator(OscillatorShape.SINE, gain = 0.16f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.055f, detuneCents = 5f)
            pitch(from = 136.hz, to = 196.hz, duration = 105.ms)
            envelope(attack = 2.ms, decay = 34.ms, sustain = 0.22f, release = 95.ms)
            lowPass(cutoff = 1_150.hz, resonance = 0.14f)
        }
        sfx(MergeMid.value) {
            oscillator(OscillatorShape.SINE, gain = 0.18f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.065f, detuneCents = 7f)
            pitch(from = 184.hz, to = 276.hz, duration = 125.ms)
            envelope(attack = 2.ms, decay = 38.ms, sustain = 0.25f, release = 125.ms)
            lowPass(cutoff = 1_650.hz, resonance = 0.15f)
        }
        sfx(MergeHigh.value) {
            oscillator(OscillatorShape.SINE, gain = 0.18f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.075f, detuneCents = 9f)
            partial(ratio = 2.01f, gain = 0.025f)
            pitch(from = 264.hz, to = 410.hz, duration = 155.ms)
            envelope(attack = 3.ms, decay = 44.ms, sustain = 0.28f, release = 175.ms)
            lowPass(cutoff = 2_200.hz, resonance = 0.16f)
        }
        sfx(ShakeLeft.value) {
            noise(NoiseColor.BROWN, gain = 0.110f, seed = 6_170_301L)
            noise(NoiseColor.PINK, gain = 0.060f, seed = 6_170_302L)
            oscillator(OscillatorShape.SINE, gain = 0.045f)
            pitch(from = 132.hz, to = 84.hz, duration = 118.ms)
            envelope(attack = 2.ms, decay = 48.ms, sustain = 0.18f, release = 125.ms)
            lowPass(cutoff = 1_250.hz, resonance = 0.18f)
        }
        sfx(ShakeRight.value) {
            noise(NoiseColor.BROWN, gain = 0.110f, seed = 6_170_303L)
            noise(NoiseColor.PINK, gain = 0.060f, seed = 6_170_304L)
            oscillator(OscillatorShape.SINE, gain = 0.045f)
            pitch(from = 102.hz, to = 148.hz, duration = 118.ms)
            envelope(attack = 2.ms, decay = 48.ms, sustain = 0.18f, release = 125.ms)
            lowPass(cutoff = 1_250.hz, resonance = 0.18f)
        }
        sfx(DangerEnter.value) {
            oscillator(OscillatorShape.SINE, gain = 0.115f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.034f, detuneCents = 9f)
            pitch(from = 430.hz, to = 335.hz, duration = 155.ms)
            envelope(attack = 4.ms, decay = 52.ms, sustain = 0.22f, release = 190.ms)
            lowPass(cutoff = 1_600.hz, resonance = 0.18f)
        }
        sfx(GameOver.value) {
            oscillator(OscillatorShape.SINE, gain = 0.14f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.05f, detuneCents = -8f)
            noise(NoiseColor.BROWN, gain = 0.022f, seed = 6_170_305L)
            pitch(from = 220.hz, to = 92.hz, duration = 360.ms)
            envelope(attack = 4.ms, decay = 110.ms, sustain = 0.35f, release = 320.ms)
            lowPass(cutoff = 900.hz, resonance = 0.12f)
        }
    }
}
