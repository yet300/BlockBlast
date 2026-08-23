package ge.yet.game.miniapp.audio

import kotlin.jvm.JvmInline

@JvmInline
value class AudioDuration private constructor(val seconds: Double) {
    companion object {
        fun seconds(value: Double): AudioDuration {
            require(value.isFinite() && value >= 0.0) { "Audio duration must be finite and non-negative" }
            return AudioDuration(value)
        }
    }
}

val Int.ms: AudioDuration get() = AudioDuration.seconds(toDouble() / 1_000.0)
val Double.seconds: AudioDuration get() = AudioDuration.seconds(this)

@JvmInline
value class Frequency private constructor(val value: Double) {
    companion object {
        fun hz(value: Double): Frequency {
            require(value.isFinite() && value > 0.0) { "Frequency must be finite and positive" }
            return Frequency(value)
        }
    }
}

val Int.hz: Frequency get() = Frequency.hz(toDouble())
val Double.hz: Frequency get() = Frequency.hz(this)

@JvmInline
value class Gain private constructor(val value: Float) {
    companion object {
        fun of(value: Float): Gain {
            require(value.isFinite() && value in 0f..4f) { "Gain must be finite and in 0..4" }
            return Gain(value)
        }
    }
}

@JvmInline
value class Pan private constructor(val value: Float) {
    companion object {
        fun of(value: Float): Pan {
            require(value.isFinite() && value in -1f..1f) { "Pan must be finite and in -1..1" }
            return Pan(value)
        }
    }
}

@JvmInline
value class Tempo private constructor(val bpm: Float) {
    companion object {
        fun of(bpm: Float): Tempo {
            require(bpm.isFinite() && bpm in 20f..400f) { "Tempo must be finite and in 20..400 BPM" }
            return Tempo(bpm)
        }
    }
}

@ConsistentCopyVisibility
data class MidiNote private constructor(val value: Int) {
    companion object {
        fun of(value: Int): MidiNote {
            require(value in 0..127) { "MIDI note must be in 0..127" }
            return MidiNote(value)
        }
    }
}

private val AUDIO_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*$")

@JvmInline value class AudioControlName(val value: String) { init { requireAudioName(value) } }
@JvmInline value class InstrumentName(val value: String) { init { requireAudioName(value) } }
@JvmInline value class MusicTrackName(val value: String) { init { requireAudioName(value) } }
@JvmInline value class SfxName(val value: String) { init { requireAudioName(value) } }

private fun requireAudioName(value: String) {
    require(AUDIO_NAME_PATTERN.matches(value)) { "Invalid audio declaration name '$value'" }
}
