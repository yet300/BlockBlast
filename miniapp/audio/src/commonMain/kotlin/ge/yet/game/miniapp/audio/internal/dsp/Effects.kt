package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.pow
import kotlin.math.round
import kotlin.math.tanh

internal fun applyDistortion(
    buffer: FloatArray,
    amount: Float,
    frameCount: Int = buffer.size,
    offset: Int = 0,
) {
    require(amount.isFinite() && amount in 0f..1f)
    require(frameCount >= 0 && offset >= 0 && offset + frameCount <= buffer.size)
    val drive = 1.0 + amount * 9.0
    val normalization = tanh(drive)
    for (frame in 0 until frameCount) {
        val index = offset + frame
        val input = buffer[index].takeIf { it.isFinite() }?.toDouble() ?: 0.0
        buffer[index] = (tanh(input * drive) / normalization).coerceIn(-1.0, 1.0).toFloat()
    }
}

internal class BitCrusherState {
    internal var held = 0f
    internal var remaining = 0
}

internal fun applyBitCrush(
    buffer: FloatArray,
    bitDepth: Int,
    sampleRateReduction: Int,
    state: BitCrusherState,
    frameCount: Int = buffer.size,
    offset: Int = 0,
) {
    require(bitDepth in 2..24 && sampleRateReduction in 1..64)
    require(frameCount >= 0 && offset >= 0 && offset + frameCount <= buffer.size)
    val levels = 2.0.pow(bitDepth - 1).toFloat() - 1f
    for (frame in 0 until frameCount) {
        val index = offset + frame
        if (state.remaining == 0) {
            val input = buffer[index].takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
            state.held = (round(input * levels) / levels).coerceIn(-1f, 1f)
            state.remaining = sampleRateReduction - 1
        } else {
            state.remaining -= 1
        }
        buffer[index] = state.held
    }
}

internal class DelayState(maxDelayFrames: Int) {
    internal val samples = FloatArray(maxDelayFrames)
    internal var writeIndex = 0

    init {
        require(maxDelayFrames > 0)
    }
}

internal fun applyDelay(
    buffer: FloatArray,
    delayFrames: Int,
    feedback: Float,
    wet: Float,
    state: DelayState,
    frameCount: Int = buffer.size,
    offset: Int = 0,
) {
    require(delayFrames in 1..state.samples.size)
    require(feedback.isFinite() && feedback in 0f..0.95f)
    require(wet.isFinite() && wet in 0f..1f)
    require(frameCount >= 0 && offset >= 0 && offset + frameCount <= buffer.size)
    var writeIndex = state.writeIndex
    for (frame in 0 until frameCount) {
        val index = offset + frame
        val input = buffer[index].takeIf { it.isFinite() } ?: 0f
        val readIndex = (writeIndex - delayFrames).floorMod(state.samples.size)
        val delayed = state.samples[readIndex]
        buffer[index] = (input + delayed * wet).takeIf { it.isFinite() } ?: 0f
        state.samples[writeIndex] = (input + delayed * feedback).coerceIn(-2f, 2f)
        writeIndex += 1
        if (writeIndex == state.samples.size) writeIndex = 0
    }
    state.writeIndex = writeIndex
}

internal class ReverbState(sampleRate: Int) {
    internal val combs: Array<FloatArray>
    internal val indices: IntArray

    init {
        require(sampleRate > 0)
        val seconds = doubleArrayOf(0.0297, 0.0371, 0.0411, 0.0437)
        combs = Array(seconds.size) { FloatArray((sampleRate * seconds[it]).toInt().coerceAtLeast(1)) }
        indices = IntArray(seconds.size)
    }
}

internal fun applyReverb(
    buffer: FloatArray,
    send: Float,
    state: ReverbState,
    frameCount: Int = buffer.size,
    offset: Int = 0,
) {
    require(send.isFinite() && send in 0f..1f)
    require(frameCount >= 0 && offset >= 0 && offset + frameCount <= buffer.size)
    for (frame in 0 until frameCount) {
        val index = offset + frame
        val input = buffer[index].takeIf { it.isFinite() } ?: 0f
        var wet = 0f
        for (combIndex in state.combs.indices) {
            val comb = state.combs[combIndex]
            val position = state.indices[combIndex]
            val delayed = comb[position]
            wet += delayed
            comb[position] = (input + delayed * 0.72f).coerceIn(-2f, 2f)
            state.indices[combIndex] = if (position + 1 == comb.size) 0 else position + 1
        }
        buffer[index] = (input + wet * (send / state.combs.size)).coerceIn(-2f, 2f)
    }
}

private fun Int.floorMod(modulus: Int): Int {
    val result = this % modulus
    return if (result < 0) result + modulus else result
}
