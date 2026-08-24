@file:Suppress("FunctionName")

package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.AudioProgramFragment
import ge.yet.game.miniapp.audio.NoiseColor
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgramFragment
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms

fun PlacementClick(
    name: String = "placement_click",
    gain: Float = 0.45f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        sfx(name) {
            oscillator(OscillatorShape.SQUARE, gain = level * 0.75f)
            noise(NoiseColor.WHITE, gain = level * 0.12f, seed = 7)
            pitch(from = 720.hz, to = 260.hz, duration = 55.ms)
            envelope(attack = 1.ms, decay = 12.ms, sustain = 0.25f, release = 45.ms)
            highPass(cutoff = 180.hz)
        }
    }
}

fun SuccessSweep(
    name: String = "success_sweep",
    gain: Float = 0.5f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        sfx(name) {
            oscillator(OscillatorShape.SINE, gain = level * 0.65f)
            oscillator(OscillatorShape.TRIANGLE, gain = level * 0.2f, detuneCents = 7f)
            pitch(from = 330.hz, to = 1_320.hz, duration = 420.ms)
            envelope(attack = 4.ms, decay = 90.ms, sustain = 0.65f, release = 260.ms)
            vibrato(rate = 6.hz, depthCents = 9f)
            highPass(cutoff = 160.hz)
        }
    }
}

fun Explosion(
    name: String = "explosion",
    seed: Long = 0,
    gain: Float = 0.55f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        sfx(name) {
            noise(NoiseColor.BROWN, gain = level * 0.55f, seed = seed)
            noise(NoiseColor.WHITE, gain = level * 0.18f, seed = seed xor -7046029254386353131L)
            oscillator(OscillatorShape.SINE, gain = level * 0.2f)
            pitch(from = 95.hz, to = 38.hz, duration = 520.ms)
            envelope(attack = 1.ms, decay = 140.ms, sustain = 0.34f, release = 720.ms)
            lowPass(cutoff = 1_400.hz, resonance = 0.18f)
            distortion(amount = 0.22f)
        }
    }
}

fun PowerUp(
    name: String = "power_up",
    gain: Float = 0.48f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    return audioProgramFragment {
        sfx(name) {
            oscillator(OscillatorShape.TRIANGLE, gain = level * 0.62f)
            oscillator(OscillatorShape.SINE, gain = level * 0.25f, detuneCents = 12f)
            pitch(from = 220.hz, to = 880.hz, duration = 360.ms)
            envelope(attack = 3.ms, decay = 70.ms, sustain = 0.68f, release = 240.ms)
            vibrato(rate = 7.hz, depthCents = 12f)
            bitCrush(bitDepth = 14, sampleRateReduction = 1)
        }
    }
}
