package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.AudioParameter
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.FilterDeclaration
import ge.yet.game.miniapp.audio.InstrumentDeclaration
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.VoiceEffectDeclaration
import kotlin.math.pow

internal class VoiceState(
    private val instrument: InstrumentDeclaration,
    note: MidiNote,
    private val sampleRate: Int,
    private val blockCapacity: Int,
    controlPositions: Map<AudioControlName, Float> = emptyMap(),
) {
    private val baseFrequency = 440.0 * 2.0.pow((note.value - 69) / 12.0)
    private val oscillatorStates = Array(instrument.oscillators.size) { OscillatorState() }
    private val noiseStates = Array(instrument.noises.size) { NoiseState(instrument.noises[it].seed) }
    private val partialStates = Array(instrument.partials.size) { OscillatorState() }
    private val fmState = OscillatorState()
    private val vibratoState = OscillatorState()
    private val envelopeState = instrument.envelope?.let {
        EnvelopeState(sampleRate, it.attack.seconds, it.decay.seconds, it.sustain, it.release.seconds)
    } ?: EnvelopeState(sampleRate, 0.0, 0.0, 1f, 0.0)
    private val filterStates = Array(instrument.filters.size) { BiquadState() }
    private val filterCoefficients = Array(instrument.filters.size) { index ->
        instrument.filters[index].coefficients(sampleRate, controlPositions)
    }
    private val crusherStates = Array(instrument.effects.size) { BitCrusherState() }

    init {
        require(sampleRate > 0 && blockCapacity > 0)
        envelopeState.noteOn()
    }

    fun render(output: FloatArray, frameCount: Int, offset: Int = 0) {
        require(frameCount in 0..blockCapacity && offset >= 0 && offset + frameCount <= output.size)
        for (frame in 0 until frameCount) {
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
}

private fun FilterDeclaration.coefficients(
    sampleRate: Int,
    controlPositions: Map<AudioControlName, Float>,
): BiquadCoefficients {
    val frequency = when (val value = frequency) {
        is AudioParameter.Constant -> value.value
        is AudioParameter.Control -> {
            val position = controlPositions[value.name]?.coerceIn(0f, 1f) ?: 0.5f
            value.outputRange.start + (value.outputRange.endInclusive - value.outputRange.start) * position
        }
    }.toDouble()
    val q = 0.5 + resonance * 9.5
    return when (this) {
        is FilterDeclaration.LowPass -> BiquadCoefficients.lowPass(sampleRate, frequency, q)
        is FilterDeclaration.HighPass -> BiquadCoefficients.highPass(sampleRate, frequency, q)
        is FilterDeclaration.BandPass -> BiquadCoefficients.bandPass(sampleRate, frequency, q)
    }
}
