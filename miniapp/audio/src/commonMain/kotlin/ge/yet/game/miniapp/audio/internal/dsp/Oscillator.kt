package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.OscillatorShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

internal class OscillatorState(internal var phase: Double = 0.0)

internal fun renderOscillator(
    shape: OscillatorShape,
    frequencyHz: Double,
    pulseWidth: Double,
    sampleRate: Int,
    state: OscillatorState,
    output: FloatArray,
    frameCount: Int,
    outputOffset: Int = 0,
) {
    require(sampleRate > 0)
    require(frameCount >= 0 && outputOffset >= 0 && outputOffset + frameCount <= output.size)
    val frequency = frequencyHz.takeIf { it.isFinite() }?.coerceIn(0.0, sampleRate * 0.49) ?: 0.0
    val width = pulseWidth.takeIf { it.isFinite() }?.coerceIn(0.01, 0.99) ?: 0.5
    val increment = frequency / sampleRate
    var phase = state.phase.moduloUnit()
    for (frame in 0 until frameCount) {
        output[outputOffset + frame] = when (shape) {
            OscillatorShape.SINE -> sin(2.0 * PI * phase).toFloat()
            OscillatorShape.TRIANGLE -> (1.0 - 4.0 * abs(phase - 0.5)).toFloat()
            OscillatorShape.SAW -> (2.0 * phase - 1.0).toFloat()
            OscillatorShape.SQUARE -> if (phase < 0.5) 1f else -1f
            OscillatorShape.PULSE -> if (phase < width) 1f else -1f
        }
        phase += increment
        if (phase >= 1.0) phase -= phase.toLong().toDouble()
    }
    state.phase = phase
}

private fun Double.moduloUnit(): Double {
    if (!isFinite()) return 0.0
    val value = this - toLong().toDouble()
    return if (value < 0.0) value + 1.0 else value
}
