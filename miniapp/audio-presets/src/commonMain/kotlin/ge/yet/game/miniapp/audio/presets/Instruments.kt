@file:Suppress("FunctionName")

package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.AudioProgramFragment
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgramFragment
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms

fun SoftPad(
    name: String = "soft_pad",
    gain: Float = 0.6f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        instrument(name) {
            oscillator(OscillatorShape.TRIANGLE, gain = level * 0.45f, detuneCents = -6f)
            oscillator(OscillatorShape.SAW, gain = level * 0.25f, detuneCents = 6f)
            oscillator(OscillatorShape.SINE, gain = level * 0.2f)
            envelope(attack = 120.ms, decay = 300.ms, sustain = 0.7f, release = 900.ms)
            vibrato(rate = 0.25.hz, depthCents = 4f)
            lowPass(cutoff = 1_800.hz, resonance = 0.15f)
        }
    }
}

fun ChipLead(
    name: String = "chip_lead",
    gain: Float = 0.45f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        instrument(name) {
            oscillator(OscillatorShape.PULSE, gain = level * 0.7f)
            oscillator(OscillatorShape.SQUARE, gain = level * 0.2f, detuneCents = 12f)
            envelope(attack = 2.ms, decay = 45.ms, sustain = 0.72f, release = 70.ms)
            vibrato(rate = 5.hz, depthCents = 7f)
            highPass(cutoff = 120.hz)
            bitCrush(bitDepth = 10, sampleRateReduction = 2)
        }
    }
}

fun AnalogBass(
    name: String = "analog_bass",
    gain: Float = 0.65f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        instrument(name) {
            oscillator(OscillatorShape.SAW, gain = level * 0.55f, detuneCents = -4f)
            oscillator(OscillatorShape.SINE, gain = level * 0.35f)
            envelope(attack = 4.ms, decay = 160.ms, sustain = 0.62f, release = 180.ms)
            lowPass(cutoff = 650.hz, resonance = 0.35f)
            distortion(amount = 0.08f)
        }
    }
}

fun GlassBell(
    name: String = "glass_bell",
    gain: Float = 0.5f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        instrument(name) {
            oscillator(OscillatorShape.SINE, gain = level * 0.5f)
            partial(ratio = 2.01f, gain = level * 0.22f)
            partial(ratio = 3.07f, gain = level * 0.12f)
            partial(ratio = 4.19f, gain = level * 0.07f)
            partial(ratio = 6.23f, gain = level * 0.04f)
            envelope(attack = 2.ms, decay = 420.ms, sustain = 0.18f, release = 1_400.ms)
            highPass(cutoff = 280.hz)
        }
    }
}
