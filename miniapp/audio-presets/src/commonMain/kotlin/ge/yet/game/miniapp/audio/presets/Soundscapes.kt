@file:Suppress("FunctionName")

package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.AudioParameter
import ge.yet.game.miniapp.audio.AudioProgramFragment
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.NoiseColor
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioParameter
import ge.yet.game.miniapp.audio.audioProgramFragment
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms
import ge.yet.game.miniapp.audio.sineLfo
import ge.yet.game.miniapp.audio.smoothNoise
import ge.yet.game.miniapp.audio.times
import ge.yet.game.pattern.degrade
import ge.yet.game.pattern.sequence

fun OceanBreeze(
    name: String = "ocean_breeze",
    seed: Long = 0L,
    gain: Float = 0.55f,
    density: Float = 0.18f,
    stereo: Float = 0.8f,
    wind: AudioParameter = audioParameter(0.55f),
    water: AudioParameter = audioParameter(0.65f),
    waves: AudioParameter = audioParameter(0.5f),
    chimes: AudioParameter = audioParameter(0.25f),
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    val eventDensity = density.requirePresetUnit("Ocean breeze density")
    val width = stereo.requirePresetUnit("Ocean breeze stereo")
    val windLevel = wind.requirePresetUnit("Ocean breeze wind")
    val waterLevel = water.requirePresetUnit("Ocean breeze water")
    val waveLevel = waves.requirePresetUnit("Ocean breeze waves")
    val chimeLevel = chimes.requirePresetUnit("Ocean breeze chimes")

    return audioProgramFragment {
        instrument("${name}_wind_voice") {
            noise(NoiseColor.BROWN, gain = 0.85f, seed = seeded(seed, 11))
            noise(NoiseColor.PINK, gain = 0.25f, seed = seeded(seed, 12))
            envelope(attack = 180.ms, decay = 120.ms, sustain = 0.92f, release = 500.ms)
            highPass(cutoff = 180.hz)
            lowPass(cutoff = 2_100.hz, resonance = 0.12f)
        }
        instrument("${name}_water_voice") {
            noise(NoiseColor.PINK, gain = 0.72f, seed = seeded(seed, 21))
            envelope(attack = 80.ms, decay = 160.ms, sustain = 0.75f, release = 420.ms)
            bandPass(center = 900.hz, resonance = 0.35f)
        }
        instrument("${name}_wave_voice") {
            noise(NoiseColor.BROWN, gain = 0.8f, seed = seeded(seed, 31))
            envelope(attack = 220.ms, decay = 240.ms, sustain = 0.7f, release = 650.ms)
            bandPass(center = 420.hz, resonance = 0.28f)
        }
        include(GlassBell(name = "${name}_bell_voice", gain = 0.72f))

        musicTrack("${name}_wind") {
            instrument("${name}_wind_voice")
            notes(MidiNote.of(48))
            gain(windLevel * smoothNoise(seeded(seed, 101), 0.7.hz, level * 0.18f..level * 0.38f))
            pan(smoothNoise(seeded(seed, 102), 0.8.hz, -width..width))
        }
        musicTrack("${name}_water") {
            instrument("${name}_water_voice")
            notes(MidiNote.of(52))
            gain(waterLevel * sineLfo(0.55.hz, level * 0.1f..level * 0.26f, phaseCycles = 0.2f))
            pan(smoothNoise(seeded(seed, 202), 0.6.hz, -width * 0.8f..width * 0.8f))
            reverb(send = 0.16f)
        }
        musicTrack("${name}_waves") {
            instrument("${name}_wave_voice")
            notes(MidiNote.of(43))
            gain(waveLevel * sineLfo(0.35.hz, level * 0.12f..level * 0.32f, phaseCycles = 0.6f))
            pan(smoothNoise(seeded(seed, 302), 0.45.hz, -width..width))
        }
        musicTrack("${name}_chimes") {
            instrument("${name}_bell_voice")
            notes(
                sequence(
                    listOf(74, 79, 81, 86, 78, 83, 88, 76).map { AudioNote.Pitched(MidiNote.of(it)) },
                ).degrade(probability = 1f - eventDensity, seed = seeded(seed, 401)),
            )
            gain(chimeLevel * audioParameter(level * 0.3f))
            pan(smoothNoise(seeded(seed, 402), 0.9.hz, -width..width))
            delay(time = 180.ms, feedback = 0.22f)
            reverb(send = 0.2f)
        }
    }
}

fun SoftRain(
    name: String = "soft_rain",
    seed: Long = 0L,
    gain: Float = 0.5f,
    density: Float = 0.35f,
    stereo: Float = 0.85f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    val eventDensity = density.requirePresetUnit("Soft rain density")
    val width = stereo.requirePresetUnit("Soft rain stereo")
    return audioProgramFragment {
        instrument("${name}_rain_voice") {
            noise(NoiseColor.PINK, gain = 0.78f, seed = seeded(seed, 510))
            noise(NoiseColor.WHITE, gain = 0.16f, seed = seeded(seed, 511))
            envelope(attack = 50.ms, decay = 120.ms, sustain = 0.86f, release = 350.ms)
            highPass(cutoff = 480.hz)
            lowPass(cutoff = 4_800.hz)
        }
        include(GlassBell(name = "${name}_drop_voice", gain = 0.38f))
        musicTrack("${name}_bed") {
            instrument("${name}_rain_voice")
            notes(MidiNote.of(60))
            gain(smoothNoise(seeded(seed, 520), 0.95.hz, level * 0.2f..level * 0.44f))
            pan(smoothNoise(seeded(seed, 521), 1.1.hz, -width..width))
        }
        musicTrack("${name}_drops") {
            instrument("${name}_drop_voice")
            notes(
                sequence(listOf(77, 84, 80, 87, 75, 82, 89, 79).map { AudioNote.Pitched(MidiNote.of(it)) })
                    .degrade(1f - eventDensity, seeded(seed, 530)),
            )
            gain(level * 0.22f)
            pan(smoothNoise(seeded(seed, 531), 1.35.hz, -width..width))
            reverb(send = 0.12f)
        }
    }
}

fun ForestNight(
    name: String = "forest_night",
    seed: Long = 0L,
    gain: Float = 0.5f,
    density: Float = 0.28f,
    stereo: Float = 0.9f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    val eventDensity = density.requirePresetUnit("Forest night density")
    val width = stereo.requirePresetUnit("Forest night stereo")
    return audioProgramFragment {
        instrument("${name}_air_voice") {
            noise(NoiseColor.BROWN, gain = 0.74f, seed = seeded(seed, 610))
            noise(NoiseColor.PINK, gain = 0.2f, seed = seeded(seed, 611))
            envelope(attack = 140.ms, decay = 200.ms, sustain = 0.82f, release = 520.ms)
            bandPass(center = 620.hz, resonance = 0.24f)
        }
        include(ChipLead(name = "${name}_insect_voice", gain = 0.34f))
        musicTrack("${name}_air") {
            instrument("${name}_air_voice")
            notes(MidiNote.of(45))
            gain(smoothNoise(seeded(seed, 620), 0.5.hz, level * 0.16f..level * 0.38f))
            pan(smoothNoise(seeded(seed, 621), 0.72.hz, -width..width))
            reverb(send = 0.1f)
        }
        musicTrack("${name}_insects") {
            instrument("${name}_insect_voice")
            notes(
                sequence(listOf(91, 95, 88, 93, 97, 90, 94, 86).map { AudioNote.Pitched(MidiNote.of(it)) })
                    .degrade(1f - eventDensity, seeded(seed, 630)),
            )
            gain(level * 0.3f)
            pan(smoothNoise(seeded(seed, 631), 1.5.hz, -width..width))
            delay(time = 95.ms, feedback = 0.14f)
        }
    }
}

fun DeepSpace(
    name: String = "deep_space",
    seed: Long = 0L,
    gain: Float = 0.52f,
    density: Float = 0.16f,
    stereo: Float = 0.88f,
): AudioProgramFragment {
    val level = gain.requirePresetGain()
    val eventDensity = density.requirePresetUnit("Deep space density")
    val width = stereo.requirePresetUnit("Deep space stereo")
    return audioProgramFragment {
        include(SoftPad(name = "${name}_pad_voice", gain = 0.72f))
        instrument("${name}_dust_voice") {
            noise(NoiseColor.BROWN, gain = 0.62f, seed = seeded(seed, 710))
            oscillator(OscillatorShape.SINE, gain = 0.18f, detuneCents = -12f)
            envelope(attack = 200.ms, decay = 260.ms, sustain = 0.78f, release = 700.ms)
            lowPass(cutoff = 780.hz, resonance = 0.2f)
        }
        include(GlassBell(name = "${name}_signal_voice", gain = 0.42f))
        musicTrack("${name}_pad") {
            instrument("${name}_pad_voice")
            notes(listOf(36, 43, 48, 55).map(MidiNote::of))
            gain(sineLfo(0.22.hz, level * 0.16f..level * 0.34f))
            pan(smoothNoise(seeded(seed, 720), 0.5.hz, -width..width))
            reverb(send = 0.22f)
        }
        musicTrack("${name}_dust") {
            instrument("${name}_dust_voice")
            notes(MidiNote.of(31))
            gain(smoothNoise(seeded(seed, 721), 0.42.hz, level * 0.12f..level * 0.3f))
            pan(smoothNoise(seeded(seed, 722), 0.58.hz, -width..width))
        }
        musicTrack("${name}_signals") {
            instrument("${name}_signal_voice")
            notes(
                sequence(listOf(72, 79, 84, 91, 76, 88, 81, 93).map { AudioNote.Pitched(MidiNote.of(it)) })
                    .degrade(1f - eventDensity, seeded(seed, 730)),
            )
            gain(level * 0.2f)
            pan(smoothNoise(seeded(seed, 731), 0.8.hz, -width..width))
            delay(time = 260.ms, feedback = 0.26f)
        }
    }
}

private fun seeded(seed: Long, salt: Long): Long = seed xor (salt * 0x9E37L)
