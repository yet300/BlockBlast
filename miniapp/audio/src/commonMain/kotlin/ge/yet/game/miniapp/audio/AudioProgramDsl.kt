package ge.yet.game.miniapp.audio

fun audioProgram(block: AudioProgramBuilder.() -> Unit): AudioProgram =
    AudioProgramBuilder().apply(block).build()

class AudioProgramBuilder internal constructor() {
    private var tempo = Tempo.of(120f)
    private val controls = linkedMapOf<AudioControlName, AudioControlDeclaration>()
    private val instruments = linkedMapOf<InstrumentName, InstrumentDeclaration>()
    private val tracks = linkedMapOf<MusicTrackName, MusicTrackDeclaration>()
    private val effects = linkedMapOf<SfxName, SoundEffectDeclaration>()

    fun tempo(bpm: Float) { tempo = Tempo.of(bpm) }

    fun control(name: String, default: Float, range: ClosedFloatingPointRange<Float>) {
        require(default.isFinite() && range.start.isFinite() && range.endInclusive.isFinite())
        require(range.start <= range.endInclusive && default in range)
        val typedName = AudioControlName(name)
        require(typedName !in controls) { "Duplicate control '$name'" }
        controls[typedName] = AudioControlDeclaration(typedName, default, range.start..range.endInclusive)
    }

    fun instrument(name: String, block: InstrumentBuilder.() -> Unit) {
        val typedName = InstrumentName(name)
        require(typedName !in instruments) { "Duplicate instrument '$name'" }
        instruments[typedName] = InstrumentBuilder().apply(block).build(typedName)
    }

    fun musicTrack(name: String, block: MusicTrackBuilder.() -> Unit) {
        val typedName = MusicTrackName(name)
        require(typedName !in tracks) { "Duplicate music track '$name'" }
        tracks[typedName] = MusicTrackBuilder().apply(block).build(typedName)
    }

    fun sfx(name: String, block: SoundEffectBuilder.() -> Unit) {
        val typedName = SfxName(name)
        require(typedName !in effects) { "Duplicate SFX '$name'" }
        effects[typedName] = SoundEffectBuilder().apply(block).build(typedName)
    }

    internal fun build() = AudioProgram(tempo, controls.values.toList(), instruments.values.toList(), tracks.values.toList(), effects.values.toList())
}

open class VoiceBuilder internal constructor() {
    private val oscillators = mutableListOf<OscillatorDeclaration>()
    private var envelope: EnvelopeDeclaration? = null

    fun oscillator(shape: OscillatorShape, gain: Float = 1f, detuneCents: Float = 0f) {
        require(detuneCents.isFinite() && detuneCents in -1_200f..1_200f)
        oscillators += OscillatorDeclaration(shape, Gain.of(gain), detuneCents)
    }

    fun envelope(attack: AudioDuration, decay: AudioDuration = 0.ms, sustain: Float = 1f, release: AudioDuration) {
        require(sustain.isFinite() && sustain in 0f..1f)
        envelope = EnvelopeDeclaration(attack, decay, sustain, release)
    }

    internal fun oscillators() = oscillators.toList()
    internal fun envelope() = envelope
}

class InstrumentBuilder internal constructor() : VoiceBuilder() {
    internal fun build(name: InstrumentName) = InstrumentDeclaration(name, oscillators(), envelope())
}

class MusicTrackBuilder internal constructor() {
    private var instrument: InstrumentName? = null
    private var notes: List<MidiNote> = emptyList()
    fun instrument(name: String) { instrument = InstrumentName(name) }
    fun notes(vararg values: MidiNote) { notes = values.toList() }
    fun notes(values: List<MidiNote>) { notes = values.toList() }
    internal fun build(name: MusicTrackName) = MusicTrackDeclaration(name, requireNotNull(instrument) { "Track requires an instrument" }, notes)
}

class SoundEffectBuilder internal constructor() : VoiceBuilder() {
    private var pitch: PitchDeclaration? = null
    fun pitch(from: Frequency, to: Frequency, duration: AudioDuration) {
        require(duration.seconds > 0.0)
        pitch = PitchDeclaration(from, to, duration)
    }
    internal fun build(name: SfxName) = SoundEffectDeclaration(name, oscillators(), envelope(), pitch)
}
