package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioMobileBudget
import ge.yet.game.miniapp.audio.FilterDeclaration
import ge.yet.game.miniapp.audio.InstrumentDeclaration
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.PitchDeclaration
import ge.yet.game.miniapp.audio.VoiceEffectDeclaration
import kotlin.math.pow

internal class VoiceState(
    private val sampleRate: Int,
    private val blockCapacity: Int,
    private val controlPositions: Map<AudioControlName, Float>,
) {
    private lateinit var instrument: InstrumentDeclaration
    private var pitch: PitchDeclaration? = null
    private var initialBaseFrequency = 440.0
    private var renderedFrames = 0L
    private val oscillatorStates = Array(AudioMobileBudget.MAX_OSCILLATORS_PER_INSTRUMENT) { OscillatorState() }
    private val noiseStates = Array(AudioMobileBudget.MAX_NOISE_SOURCES) { NoiseState(1L) }
    private val partialStates = Array(AudioMobileBudget.MAX_ADDITIVE_PARTIALS) { OscillatorState() }
    private val fmState = OscillatorState()
    private val vibratoState = OscillatorState()
    private val envelopeState = EnvelopeState(sampleRate, 0.0, 0.0, 1f, 0.0)
    private val filterStates = Array(AudioMobileBudget.MAX_FILTERS) { BiquadState() }
    private val filterCoefficients = Array(AudioMobileBudget.MAX_FILTERS) {
        BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
    }
    private val crusherStates = Array(AudioMobileBudget.MAX_VOICE_EFFECTS) { BitCrusherState() }

    constructor(
        instrument: InstrumentDeclaration,
        note: MidiNote,
        sampleRate: Int,
        blockCapacity: Int,
        controlPositions: Map<AudioControlName, Float> = emptyMap(),
        pitch: PitchDeclaration? = null,
    ) : this(sampleRate, blockCapacity, controlPositions) {
        reset(instrument, note, pitch)
    }

    init {
        require(sampleRate > 0 && blockCapacity > 0)
    }

    fun reset(instrument: InstrumentDeclaration, note: MidiNote, pitch: PitchDeclaration? = null) {
        this.instrument = instrument
        this.pitch = pitch
        initialBaseFrequency = 440.0 * 2.0.pow((note.value - 69) / 12.0)
        renderedFrames = 0L
        oscillatorStates.forEach { it.phase = 0.0 }
        noiseStates.forEachIndexed { index, state ->
            state.reset(instrument.noises.getOrNull(index)?.seed ?: 1L)
        }
        partialStates.forEach { it.phase = 0.0 }
        fmState.phase = 0.0
        vibratoState.phase = 0.0
        val envelope = instrument.envelope
        envelopeState.reset(
            sampleRate = sampleRate,
            attackSeconds = envelope?.attack?.seconds ?: 0.0,
            decaySeconds = envelope?.decay?.seconds ?: 0.0,
            sustain = envelope?.sustain ?: 1f,
            releaseSeconds = envelope?.release?.seconds ?: 0.0,
        )
        envelopeState.noteOn()
        filterStates.forEach { state ->
            state.x1 = 0.0
            state.x2 = 0.0
            state.y1 = 0.0
            state.y2 = 0.0
        }
        for (index in instrument.filters.indices) {
            filterCoefficients[index] = instrument.filters[index].coefficients(sampleRate, controlPositions)
        }
        crusherStates.forEach { state ->
            state.held = 0f
            state.remaining = 0
        }
    }

    fun render(output: FloatArray, frameCount: Int, offset: Int = 0) {
        require(frameCount in 0..blockCapacity && offset >= 0 && offset + frameCount <= output.size)
        for (frame in 0 until frameCount) {
            val baseFrequency = baseFrequency()
            val fm = instrument.frequencyModulation?.let {
                nextOscillatorSample(ge.yet.game.miniapp.audio.OscillatorShape.SINE, baseFrequency * it.ratio, 0.5, sampleRate, fmState) * it.index
            } ?: 0f
            val vibratoCents = instrument.vibrato?.let {
                nextOscillatorSample(ge.yet.game.miniapp.audio.OscillatorShape.SINE, it.rate.value, 0.5, sampleRate, vibratoState) * it.depthCents
            } ?: 0f
            val frequency = baseFrequency * 2.0.pow((vibratoCents + fm * 12f) / 1200.0)
            var sample = 0f
            for (index in instrument.oscillators.indices) {
                val oscillator = instrument.oscillators[index]
                val detuned = frequency * 2.0.pow(oscillator.detuneCents / 1200.0)
                sample += nextOscillatorSample(oscillator.shape, detuned, 0.5, sampleRate, oscillatorStates[index]) * oscillator.gain.value
            }
            for (index in instrument.noises.indices) {
                sample += nextNoiseSample(instrument.noises[index].color, noiseStates[index]) * instrument.noises[index].gain.value
            }
            for (index in instrument.partials.indices) {
                val partial = instrument.partials[index]
                sample += nextOscillatorSample(ge.yet.game.miniapp.audio.OscillatorShape.SINE, frequency * partial.ratio, 0.5, sampleRate, partialStates[index]) * partial.gain.value
            }
            output[offset + frame] = (sample * envelopeState.nextValue()).takeIf { it.isFinite() } ?: 0f
            renderedFrames += 1
        }
        for (index in instrument.filters.indices) {
            processBiquad(output, filterCoefficients[index], filterStates[index], frameCount, offset)
        }
        for (index in instrument.effects.indices) {
            when (val effect = instrument.effects[index]) {
                is VoiceEffectDeclaration.Distortion -> applyDistortion(output, effect.amount, frameCount, offset)
                is VoiceEffectDeclaration.BitCrush -> applyBitCrush(
                    output, effect.bitDepth, effect.sampleRateReduction, crusherStates[index], frameCount, offset,
                )
            }
        }
    }

    fun noteOff() = envelopeState.noteOff()

    val isFinished: Boolean get() = envelopeState.phase == EnvelopePhase.DONE

    private fun baseFrequency(): Double {
        val sweep = pitch ?: return initialBaseFrequency
        val durationFrames = (sweep.duration.seconds * sampleRate).toLong().coerceAtLeast(1L)
        val position = (renderedFrames.toDouble() / durationFrames).coerceIn(0.0, 1.0)
        return sweep.from.value + (sweep.to.value - sweep.from.value) * position
    }
}

private fun FilterDeclaration.coefficients(
    sampleRate: Int,
    controlPositions: Map<AudioControlName, Float>,
): BiquadCoefficients {
    val frequency = evaluateAudioParameter(frequency, 0, sampleRate, controlPositions).toDouble()
    val q = 0.5 + resonance * 9.5
    return when (this) {
        is FilterDeclaration.LowPass -> BiquadCoefficients.lowPass(sampleRate, frequency, q)
        is FilterDeclaration.HighPass -> BiquadCoefficients.highPass(sampleRate, frequency, q)
        is FilterDeclaration.BandPass -> BiquadCoefficients.bandPass(sampleRate, frequency, q)
    }
}
