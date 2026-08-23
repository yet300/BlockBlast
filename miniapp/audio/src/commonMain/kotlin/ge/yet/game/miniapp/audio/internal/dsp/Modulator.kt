package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.PI
import kotlin.math.sin

internal class LfoState(internal var phase: Double = 0.0)

internal fun renderSineLfo(
    frequencyHz: Double,
    sampleRate: Int,
    state: LfoState,
    output: FloatArray,
    frameCount: Int,
    outputOffset: Int = 0,
) {
    require(sampleRate > 0 && frameCount >= 0 && outputOffset >= 0 && outputOffset + frameCount <= output.size)
    val frequency = frequencyHz.takeIf { it.isFinite() }?.coerceIn(0.0, sampleRate * 0.49) ?: 0.0
    val increment = frequency / sampleRate
    var phase = state.phase.takeIf { it.isFinite() }?.let { it - it.toLong() } ?: 0.0
    if (phase < 0.0) phase += 1.0
    for (frame in 0 until frameCount) {
        output[outputOffset + frame] = sin(2.0 * PI * phase).toFloat()
        phase += increment
        if (phase >= 1.0) phase -= phase.toLong().toDouble()
    }
    state.phase = phase
}

internal class SmoothNoiseState(seed: Long) {
    internal val random = NoiseState(seed)
    internal var start = random.nextWhite()
    internal var end = random.nextWhite()
    internal var position = 0
}

internal fun renderSmoothNoise(
    frequencyHz: Double,
    sampleRate: Int,
    state: SmoothNoiseState,
    output: FloatArray,
    frameCount: Int,
    outputOffset: Int = 0,
) {
    require(sampleRate > 0 && frameCount >= 0 && outputOffset >= 0 && outputOffset + frameCount <= output.size)
    val frequency = frequencyHz.takeIf { it.isFinite() }?.coerceIn(0.001, sampleRate.toDouble()) ?: 0.001
    val segmentFrames = (sampleRate / frequency).toInt().coerceAtLeast(1)
    for (frame in 0 until frameCount) {
        val t = state.position.toDouble() / segmentFrames
        val smooth = t * t * (3.0 - 2.0 * t)
        output[outputOffset + frame] = (state.start + (state.end - state.start) * smooth).coerceIn(-1.0, 1.0).toFloat()
        state.position += 1
        if (state.position >= segmentFrames) {
            state.position = 0
            state.start = state.end
            state.end = state.random.nextWhite()
        }
    }
}
