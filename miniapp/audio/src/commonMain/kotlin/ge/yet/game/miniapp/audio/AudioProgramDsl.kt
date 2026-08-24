package ge.yet.game.miniapp.audio

import ge.yet.game.pattern.Pattern
import ge.yet.game.pattern.sequence

fun audioProgram(block: AudioProgramBuilder.() -> Unit): AudioProgram =
    AudioProgramBuilder().apply(block).build()

class AudioProgramBuilder internal constructor() {
    private var tempo = Tempo.of(120f)
    private val controls = linkedMapOf<AudioControlName, AudioControlDeclaration>()
    private val instruments = linkedMapOf<InstrumentName, InstrumentDeclaration>()
    private val tracks = linkedMapOf<MusicTrackName, MusicTrackDeclaration>()
    private val effects = linkedMapOf<SfxName, SoundEffectDeclaration>()
    private var musicBus = AudioBusDeclaration(emptyList())
    private var sfxBus = AudioBusDeclaration(emptyList())

    fun tempo(bpm: Float) { tempo = Tempo.of(bpm) }

    fun control(name: String, default: Float, range: ClosedFloatingPointRange<Float>): AudioControlReference {
        require(default.isFinite() && range.start.isFinite() && range.endInclusive.isFinite())
        require(range.start <= range.endInclusive && default in range)
        val typedName = AudioControlName(name)
        require(typedName !in controls) { "Duplicate control '$name'" }
        controls[typedName] = AudioControlDeclaration(typedName, default, range.start..range.endInclusive)
        return AudioControlReference(typedName)
    }

    fun control(name: String): AudioControlReference = AudioControlReference(AudioControlName(name))

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

    fun include(fragment: AudioProgramFragment) {
        fragment.program.controls.forEach { declaration ->
            require(declaration.name !in controls) { "Duplicate control '${declaration.name.value}'" }
        }
        fragment.program.instruments.forEach { declaration ->
            require(declaration.name !in instruments) { "Duplicate instrument '${declaration.name.value}'" }
        }
        fragment.program.musicTracks.forEach { declaration ->
            require(declaration.name !in tracks) { "Duplicate music track '${declaration.name.value}'" }
        }
        fragment.program.soundEffects.forEach { declaration ->
            require(declaration.name !in effects) { "Duplicate SFX '${declaration.name.value}'" }
        }
        fragment.program.controls.forEach { controls[it.name] = it }
        fragment.program.instruments.forEach { instruments[it.name] = it }
        fragment.program.musicTracks.forEach { tracks[it.name] = it }
        fragment.program.soundEffects.forEach { effects[it.name] = it }
        musicBus = AudioBusDeclaration(musicBus.effects + fragment.program.musicBus.effects)
        sfxBus = AudioBusDeclaration(sfxBus.effects + fragment.program.sfxBus.effects)
    }

    fun musicBus(block: SendEffectBuilder.() -> Unit) {
        musicBus = AudioBusDeclaration(SendEffectBuilder().apply(block).effects())
    }

    fun sfxBus(block: SendEffectBuilder.() -> Unit) {
        sfxBus = AudioBusDeclaration(SendEffectBuilder().apply(block).effects())
    }

    internal fun build() = AudioProgram(
        tempo,
        controls.values.toList(),
        instruments.values.toList(),
        tracks.values.toList(),
        effects.values.toList(),
        musicBus,
        sfxBus,
    )
}

open class VoiceBuilder internal constructor() {
    private val oscillators = mutableListOf<OscillatorDeclaration>()
    private val noises = mutableListOf<NoiseDeclaration>()
    private val partials = mutableListOf<AdditivePartialDeclaration>()
    private val filters = mutableListOf<FilterDeclaration>()
    private val effects = mutableListOf<VoiceEffectDeclaration>()
    private var envelope: EnvelopeDeclaration? = null
    private var frequencyModulation: FrequencyModulationDeclaration? = null
    private var vibrato: VibratoDeclaration? = null

    fun oscillator(shape: OscillatorShape, gain: Float = 1f, detuneCents: Float = 0f) {
        require(detuneCents.isFinite() && detuneCents in -1_200f..1_200f)
        oscillators += OscillatorDeclaration(shape, Gain.of(gain), detuneCents)
    }

    fun noise(color: NoiseColor, gain: Float = 1f, seed: Long = 0L) {
        noises += NoiseDeclaration(color, Gain.of(gain), seed)
    }

    fun partial(ratio: Float, gain: Float = 1f) {
        require(ratio.isFinite() && ratio > 0f)
        partials += AdditivePartialDeclaration(ratio, Gain.of(gain))
    }

    fun frequencyModulation(ratio: Float, index: Float) {
        require(ratio.isFinite() && ratio > 0f && index.isFinite() && index >= 0f)
        frequencyModulation = FrequencyModulationDeclaration(ratio, index)
    }

    fun vibrato(rate: Frequency, depthCents: Float) {
        require(depthCents.isFinite() && depthCents in 0f..1_200f)
        vibrato = VibratoDeclaration(rate, depthCents)
    }

    fun lowPass(cutoff: Frequency, resonance: Float = 0f) =
        lowPass(AudioParameter.Constant(cutoff.value.toFloat()), resonance)

    fun lowPass(cutoff: AudioParameter, resonance: Float = 0f) {
        filters += FilterDeclaration.LowPass(cutoff, resonance.validResonance())
    }

    fun highPass(cutoff: Frequency, resonance: Float = 0f) =
        highPass(AudioParameter.Constant(cutoff.value.toFloat()), resonance)

    fun highPass(cutoff: AudioParameter, resonance: Float = 0f) {
        filters += FilterDeclaration.HighPass(cutoff, resonance.validResonance())
    }

    fun bandPass(center: Frequency, resonance: Float = 0f) =
        bandPass(AudioParameter.Constant(center.value.toFloat()), resonance)

    fun bandPass(center: AudioParameter, resonance: Float = 0f) {
        filters += FilterDeclaration.BandPass(center, resonance.validResonance())
    }

    fun distortion(amount: Float) {
        require(amount.isFinite() && amount in 0f..1f)
        effects += VoiceEffectDeclaration.Distortion(amount)
    }

    fun bitCrush(bitDepth: Int, sampleRateReduction: Int = 1) {
        require(bitDepth in 2..24 && sampleRateReduction in 1..64)
        effects += VoiceEffectDeclaration.BitCrush(bitDepth, sampleRateReduction)
    }

    fun envelope(attack: AudioDuration, decay: AudioDuration = 0.ms, sustain: Float = 1f, release: AudioDuration) {
        require(sustain.isFinite() && sustain in 0f..1f)
        envelope = EnvelopeDeclaration(attack, decay, sustain, release)
    }

    internal fun oscillators() = oscillators.toList()
    internal fun noises() = noises.toList()
    internal fun partials() = partials.toList()
    internal fun envelope() = envelope
    internal fun frequencyModulation() = frequencyModulation
    internal fun vibrato() = vibrato
    internal fun filters() = filters.toList()
    internal fun voiceEffects() = effects.toList()
}

class InstrumentBuilder internal constructor() : VoiceBuilder() {
    internal fun build(name: InstrumentName) = InstrumentDeclaration(
        name, oscillators(), noises(), partials(), envelope(), frequencyModulation(), vibrato(), filters(), voiceEffects(),
    )
}

class MusicTrackBuilder internal constructor() : SendEffectBuilder() {
    private var instrument: InstrumentName? = null
    private var pattern: Pattern<AudioNote>? = null
    private var gain: AudioParameter = AudioParameter.Constant(1f)
    private var pan: AudioParameter = AudioParameter.Constant(0f)
    fun instrument(name: String) { instrument = InstrumentName(name) }
    fun notes(vararg values: MidiNote) { notes(values.toList()) }
    fun notes(values: List<MidiNote>) {
        require(values.isNotEmpty()) { "A music track requires at least one note" }
        pattern = sequence(values.map(AudioNote::Pitched))
    }
    fun notes(value: Pattern<AudioNote>) { pattern = value }
    fun gain(value: Float) {
        require(value.isFinite())
        gain(AudioParameter.Constant(value))
    }
    fun gain(value: AudioParameter) { gain = value }
    fun pan(value: Float) {
        require(value.isFinite())
        pan(AudioParameter.Constant(value))
    }
    fun pan(value: AudioParameter) { pan = value }
    internal fun build(name: MusicTrackName) = MusicTrackDeclaration(
        name,
        requireNotNull(instrument) { "Track requires an instrument" },
        requireNotNull(pattern) { "Track requires a note pattern" },
        gain,
        pan,
        effects(),
    )
}

class SoundEffectBuilder internal constructor() : VoiceBuilder() {
    private var pitch: PitchDeclaration? = null
    fun pitch(from: Frequency, to: Frequency, duration: AudioDuration) {
        require(duration.seconds > 0.0)
        pitch = PitchDeclaration(from, to, duration)
    }
    internal fun build(name: SfxName) = SoundEffectDeclaration(
        name, oscillators(), noises(), partials(), envelope(), pitch, frequencyModulation(), vibrato(), filters(), voiceEffects(),
    )
}

open class SendEffectBuilder internal constructor() {
    private val sendEffects = mutableListOf<SendEffectDeclaration>()

    fun delay(time: AudioDuration, feedback: Float) {
        require(time.seconds > 0.0 && feedback.isFinite() && feedback in 0f..<1f)
        sendEffects += SendEffectDeclaration.Delay(time, feedback)
    }

    fun reverb(send: Float) {
        require(send.isFinite() && send in 0f..1f)
        sendEffects += SendEffectDeclaration.Reverb(send)
    }

    internal fun effects(): List<SendEffectDeclaration> = sendEffects.toList()
}

private fun Float.validResonance(): Float {
    require(isFinite() && this in 0f..1f)
    return this
}
