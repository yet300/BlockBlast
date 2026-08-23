package ge.yet.game.miniapp.audio

enum class OscillatorShape { SINE, TRIANGLE, SAW, SQUARE, PULSE }

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
    val envelope: EnvelopeDeclaration?,
)

@ConsistentCopyVisibility
data class MusicTrackDeclaration internal constructor(
    val name: MusicTrackName,
    val instrument: InstrumentName,
    val notes: List<MidiNote>,
)

@ConsistentCopyVisibility
data class SoundEffectDeclaration internal constructor(
    val name: SfxName,
    val oscillators: List<OscillatorDeclaration>,
    val envelope: EnvelopeDeclaration?,
    val pitch: PitchDeclaration?,
)
