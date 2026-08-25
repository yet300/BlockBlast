package ge.yet.game.twentyfortyeight.audio

import ge.yet.game.miniapp.audio.NoiseColor
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgramFragment
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms

internal object TwentyFortyEightSfx {
    val fragment = audioProgramFragment {
        sfx(TwentyFortyEightAudio.MergeLow.value) {
            oscillator(OscillatorShape.SINE, gain = 0.18f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.07f, detuneCents = 5f)
            pitch(from = 185.hz, to = 142.hz, duration = 90.ms)
            envelope(attack = 2.ms, decay = 28.ms, sustain = 0.24f, release = 85.ms)
            lowPass(cutoff = 1_250.hz, resonance = 0.12f)
        }
        sfx(TwentyFortyEightAudio.MergeMid.value) {
            oscillator(OscillatorShape.SINE, gain = 0.19f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.08f, detuneCents = 7f)
            pitch(from = 260.hz, to = 196.hz, duration = 105.ms)
            envelope(attack = 2.ms, decay = 34.ms, sustain = 0.26f, release = 105.ms)
            lowPass(cutoff = 1_700.hz, resonance = 0.14f)
        }
        sfx(TwentyFortyEightAudio.MergeHigh.value) {
            oscillator(OscillatorShape.SINE, gain = 0.19f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.09f, detuneCents = 9f)
            partial(ratio = 2.01f, gain = 0.035f)
            pitch(from = 370.hz, to = 278.hz, duration = 135.ms)
            envelope(attack = 3.ms, decay = 42.ms, sustain = 0.28f, release = 150.ms)
            lowPass(cutoff = 2_200.hz, resonance = 0.16f)
        }
        sfx(TwentyFortyEightAudio.Undo.value) {
            oscillator(OscillatorShape.SINE, gain = 0.16f)
            noise(NoiseColor.PINK, gain = 0.025f, seed = 2_048_301L)
            pitch(from = 210.hz, to = 420.hz, duration = 145.ms)
            envelope(attack = 8.ms, decay = 45.ms, sustain = 0.3f, release = 115.ms)
            highPass(cutoff = 140.hz)
        }
        sfx(TwentyFortyEightAudio.GameOver.value) {
            oscillator(OscillatorShape.SINE, gain = 0.15f)
            oscillator(OscillatorShape.TRIANGLE, gain = 0.055f, detuneCents = -8f)
            pitch(from = 247.hz, to = 123.hz, duration = 310.ms)
            envelope(attack = 4.ms, decay = 90.ms, sustain = 0.42f, release = 260.ms)
            lowPass(cutoff = 980.hz, resonance = 0.10f)
        }
    }
}
