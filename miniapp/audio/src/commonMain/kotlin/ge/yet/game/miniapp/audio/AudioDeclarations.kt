package ge.yet.game.miniapp.audio

import ge.yet.game.pattern.Pattern

enum class OscillatorShape { SINE, TRIANGLE, SAW, SQUARE, PULSE }
enum class NoiseColor { WHITE, PINK, BROWN }

sealed interface AudioNote {
    data class Pitched(val midi: MidiNote) : AudioNote
    data object Rest : AudioNote
}

sealed interface AudioParameter {
    val outputRange: ClosedFloatingPointRange<Float>

    @ConsistentCopyVisibility
    data class Constant internal constructor(val value: Float) : AudioParameter {
        override val outputRange: ClosedFloatingPointRange<Float> = value..value
    }

    @ConsistentCopyVisibility
    data class Control internal constructor(
        val name: AudioControlName,
        override val outputRange: ClosedFloatingPointRange<Float>,
    ) : AudioParameter
}

class AudioControlReference internal constructor(private val name: AudioControlName) {
    fun map(outputStart: Float, outputEndInclusive: Float): AudioParameter {
        require(outputStart.isFinite() && outputEndInclusive.isFinite() && outputStart <= outputEndInclusive)
        return AudioParameter.Control(name, outputStart..outputEndInclusive)
    }
}

@ConsistentCopyVisibility
data class AudioControlDeclaration internal constructor(
    val name: AudioControlName,
    val default: Float,
    val range: ClosedFloatingPointRange<Float>,
)

@ConsistentCopyVisibility
data class OscillatorDeclaration internal constructor(
    val shape: OscillatorShape,
    val gain: Gain,
    val detuneCents: Float,
)

@ConsistentCopyVisibility
data class NoiseDeclaration internal constructor(
    val color: NoiseColor,
    val gain: Gain,
    val seed: Long,
)

@ConsistentCopyVisibility
data class AdditivePartialDeclaration internal constructor(
    val ratio: Float,
    val gain: Gain,
)

@ConsistentCopyVisibility
data class FrequencyModulationDeclaration internal constructor(
    val ratio: Float,
    val index: Float,
)

@ConsistentCopyVisibility
data class VibratoDeclaration internal constructor(
    val rate: Frequency,
    val depthCents: Float,
)

sealed interface FilterDeclaration {
    val frequency: AudioParameter
    val resonance: Float

    @ConsistentCopyVisibility
    data class LowPass internal constructor(
        override val frequency: AudioParameter,
        override val resonance: Float,
    ) : FilterDeclaration

    @ConsistentCopyVisibility
    data class HighPass internal constructor(
        override val frequency: AudioParameter,
        override val resonance: Float,
    ) : FilterDeclaration

    @ConsistentCopyVisibility
    data class BandPass internal constructor(
        override val frequency: AudioParameter,
        override val resonance: Float,
    ) : FilterDeclaration
}

sealed interface VoiceEffectDeclaration {
    @ConsistentCopyVisibility
    data class Distortion internal constructor(val amount: Float) : VoiceEffectDeclaration

    @ConsistentCopyVisibility
    data class BitCrush internal constructor(
        val bitDepth: Int,
        val sampleRateReduction: Int,
    ) : VoiceEffectDeclaration
}

sealed interface SendEffectDeclaration {
    @ConsistentCopyVisibility
    data class Delay internal constructor(
        val time: AudioDuration,
        val feedback: Float,
    ) : SendEffectDeclaration

    @ConsistentCopyVisibility
    data class Reverb internal constructor(val send: Float) : SendEffectDeclaration
}

@ConsistentCopyVisibility
data class AudioBusDeclaration internal constructor(
    val effects: List<SendEffectDeclaration>,
)

@ConsistentCopyVisibility
data class EnvelopeDeclaration internal constructor(
    val attack: AudioDuration,
    val decay: AudioDuration,
    val sustain: Float,
    val release: AudioDuration,
)

@ConsistentCopyVisibility
data class PitchDeclaration internal constructor(
    val from: Frequency,
    val to: Frequency,
    val duration: AudioDuration,
)

@ConsistentCopyVisibility
data class InstrumentDeclaration internal constructor(
    val name: InstrumentName,
    val oscillators: List<OscillatorDeclaration>,
    val noises: List<NoiseDeclaration>,
    val partials: List<AdditivePartialDeclaration>,
    val envelope: EnvelopeDeclaration?,
    val frequencyModulation: FrequencyModulationDeclaration?,
    val vibrato: VibratoDeclaration?,
    val filters: List<FilterDeclaration>,
    val effects: List<VoiceEffectDeclaration>,
)

@ConsistentCopyVisibility
data class MusicTrackDeclaration internal constructor(
    val name: MusicTrackName,
    val instrument: InstrumentName,
    val pattern: Pattern<AudioNote>,
    val effects: List<SendEffectDeclaration>,
)

@ConsistentCopyVisibility
data class SoundEffectDeclaration internal constructor(
    val name: SfxName,
    val oscillators: List<OscillatorDeclaration>,
    val noises: List<NoiseDeclaration>,
    val partials: List<AdditivePartialDeclaration>,
    val envelope: EnvelopeDeclaration?,
    val pitch: PitchDeclaration?,
    val frequencyModulation: FrequencyModulationDeclaration?,
    val vibrato: VibratoDeclaration?,
    val filters: List<FilterDeclaration>,
    val effects: List<VoiceEffectDeclaration>,
)
