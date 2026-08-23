package ge.yet.game.miniapp.audio

enum class OscillatorShape { SINE, TRIANGLE, SAW, SQUARE, PULSE }

data class AudioControlDeclaration(
    val name: AudioControlName,
    val default: Float,
    val range: ClosedFloatingPointRange<Float>,
)

data class OscillatorDeclaration(
    val shape: OscillatorShape,
    val gain: Gain,
    val detuneCents: Float,
)

data class EnvelopeDeclaration(
    val attack: AudioDuration,
    val decay: AudioDuration,
    val sustain: Float,
    val release: AudioDuration,
)

data class PitchDeclaration(
    val from: Frequency,
    val to: Frequency,
    val duration: AudioDuration,
)

data class InstrumentDeclaration(
    val name: InstrumentName,
    val oscillators: List<OscillatorDeclaration>,
    val envelope: EnvelopeDeclaration?,
)

data class MusicTrackDeclaration(
    val name: MusicTrackName,
    val instrument: InstrumentName,
    val notes: List<MidiNote>,
)

data class SoundEffectDeclaration(
    val name: SfxName,
    val oscillators: List<OscillatorDeclaration>,
    val envelope: EnvelopeDeclaration?,
    val pitch: PitchDeclaration?,
)
